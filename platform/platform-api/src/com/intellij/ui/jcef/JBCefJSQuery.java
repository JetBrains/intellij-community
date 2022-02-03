// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.jcef.JBCefClient.JSQueryPool;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandler;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

/**
 * A JS query callback.
 *
 * @author tav
 */
public final class JBCefJSQuery implements JBCefDisposable {
  private final @NotNull JSQueryFunc myFunc;
  private final @NotNull JBCefClient myJBCefClient;
  private final @NotNull DisposeHelper myDisposeHelper = new DisposeHelper();

  private final @NotNull Map<Function<? super String, ? extends Response>, CefMessageRouterHandler> myHandlerMap =
    Collections.synchronizedMap(new HashMap<>());

  private JBCefJSQuery(@NotNull JBCefBrowserBase browser, @NotNull JBCefJSQuery.JSQueryFunc func) {
    myFunc = func;
    myJBCefClient = browser.getJBCefClient();
    Disposer.register(browser.getJBCefClient(), this);
  }

  /**
   * @return name of the global function JS must call to send query to Java
   */
  @NotNull
  public String getFuncName() {
    return myFunc.myFuncName;
  }

  /**
   * Creates a unique JS query.
   *
   * @see JBCefClient.Properties#JS_QUERY_POOL_SIZE
   * @param browser the associated cef browser
   */
  @NotNull
  public static JBCefJSQuery create(@NotNull JBCefBrowserBase browser) {
    Function<Void, JBCefJSQuery> create = (v) -> {
      return new JBCefJSQuery(browser, new JSQueryFunc(browser.getJBCefClient()));
    };
    if (!browser.isCefBrowserCreateStarted()) {
      return create.apply(null);
    }
    JSQueryPool pool = browser.getJBCefClient().getJSQueryPool();
    JSQueryFunc slot;
    if (pool != null && (slot = pool.useFreeSlot()) != null) {
      return new JBCefJSQuery(browser, slot);
    }
    Logger.getInstance(JBCefJSQuery.class).
      warn("Set the property JBCefClient.Properties.JS_QUERY_POOL_SIZE to use JBCefJSQuery after the browser has been created",
            new IllegalStateException());
    // this query will produce en error in JS debug console like: "Uncaught TypeError: window.cefQuery_0123456789_1 is not a function"
    return create.apply(null);
  }

  /**
   * @deprecated use {@link #create(JBCefBrowserBase)}
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  @Deprecated
  public static JBCefJSQuery create(@NotNull JBCefBrowser browser) {
    return create((JBCefBrowserBase)browser);
  }

  /**
   * Returns the query callback to inject into JS code
   *
   * @param queryResult the result (JS variable name, or JS value in single quotes) that will be passed to the java handler {@link #addHandler(Function)}
   */
  @NotNull
  public String inject(@Nullable String queryResult) {
    return inject(queryResult, "function(response) {}", "function(error_code, error_message) {}");
  }

  /**
   * Returns the query callback to inject into JS code
   *
   * @param queryResult the result (JS variable name, or JS value in single quotes) that will be passed to the java handler {@link #addHandler(Function)}
   * @param onSuccessCallback JS callback in format: function(response) {}
   * @param onFailureCallback JS callback in format: function(error_code, error_message) {}
   */
  @NotNull
  public String inject(@Nullable String queryResult, @NotNull String onSuccessCallback, @NotNull String onFailureCallback) {
    checkDisposed();

    if (queryResult != null && queryResult.isEmpty()) queryResult = "''";
    return "window." + myFunc.myFuncName +
           "({request: '' + " + queryResult + "," +
             "onSuccess: " + onSuccessCallback + "," +
             "onFailure: " + onFailureCallback +
           "});";
  }

  public void addHandler(@NotNull Function<? super String, ? extends Response> handler) {
    checkDisposed();

    CefMessageRouterHandler cefHandler;
    myFunc.myRouter.addHandler(cefHandler = new CefMessageRouterHandlerAdapter() {
      @Override
      public boolean onQuery(CefBrowser browser,
                             CefFrame frame,
                             long query_id,
                             String request,
                             boolean persistent,
                             CefQueryCallback callback)
      {
        Response response = handler.apply(request);
        if (callback != null && response != null) {
          if (response.isSuccess() && response.hasResponse()) {
            callback.success(response.response());
          } else {
            callback.failure(response.errCode(), response.errMsg());
          }
        } else if (callback != null) {
          callback.success("");
        }
        return true;
      }
    }, false);
    myHandlerMap.put(handler, cefHandler);
  }

  public void removeHandler(@NotNull Function<? super String, ? extends Response> function) {
    CefMessageRouterHandler cefHandler;
    cefHandler = myHandlerMap.remove(function);
    if (cefHandler != null) {
      myFunc.myRouter.removeHandler(cefHandler);
    }
  }

  public void clearHandlers() {
    List<Function<? super String, ? extends Response>> functions = new ArrayList<>(myHandlerMap.size());
    // Collection.synchronizedMap object is the internal mutex for the collection.
    synchronized (myHandlerMap) {
      myHandlerMap.forEach((func, handler) -> functions.add(func));
    }
    functions.forEach(func -> removeHandler(func));
  }

  @Override
  public void dispose() {
    myDisposeHelper.dispose(() -> {
      if (myFunc.myIsSlot) {
        JSQueryPool pool = myJBCefClient.getJSQueryPool();
        if (pool != null) {
          clearHandlers();
          pool.releaseUsedSlot(myFunc);
          return;
        }
      }
      myJBCefClient.getCefClient().removeMessageRouter(myFunc.myRouter);
      myFunc.myRouter.dispose();
      myHandlerMap.clear();
    });
  }

  @Override
  public boolean isDisposed() {
    return myDisposeHelper.isDisposed();
  }

  private void checkDisposed() {
    if (isDisposed()) throw new IllegalStateException("the JS query has been disposed");
  }

  /**
   * A JS handler response to a query.
   */
  public static class Response {
    public static final int ERR_CODE_SUCCESS = 0;

    @Nullable private final String myResponse;
    private final int myErrCode;
    @Nullable private final String myErrMsg;


    public Response(@Nullable String response) {
      this(response, ERR_CODE_SUCCESS, null);
    }

    public Response(@Nullable String response, int errCode, @Nullable String errMsg) {
      myResponse = response;
      myErrCode = errCode;
      myErrMsg = errMsg;
    }

    @Nullable
    public String response() {
      return myResponse;
    }

    public int errCode() {
      return myErrCode;
    }

    @Nullable
    public String errMsg() {
      return myErrMsg;
    }

    public boolean isSuccess() {
      return myErrCode == ERR_CODE_SUCCESS;
    }

    public boolean hasResponse() {
      return myResponse != null;
    }
  }

  static class JSQueryFunc {
    final @NotNull CefMessageRouter myRouter;
    final @NotNull String myFuncName;
    final boolean myIsSlot;

    JSQueryFunc(@NotNull JBCefClient client) {
      this(client, client.nextJSQueryIndex(), false);
    }

    JSQueryFunc(@NotNull JBCefClient client, int index, boolean isSlot) {
      String postfix = client.hashCode() + "_" + (isSlot ? "slot_" : "") + index;
      myIsSlot = isSlot;
      myFuncName = "cefQuery_" + postfix;
      CefMessageRouter.CefMessageRouterConfig config = new CefMessageRouter.CefMessageRouterConfig();
      config.jsQueryFunction = myFuncName;
      config.jsCancelFunction = "cefQuery_cancel_" + postfix;
      myRouter = CefMessageRouter.create(config);
      client.getCefClient().addMessageRouter(myRouter);
    }
  }
}
