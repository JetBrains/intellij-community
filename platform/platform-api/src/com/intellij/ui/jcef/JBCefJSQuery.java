// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.openapi.util.Disposer;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandler;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * A JS query callback.
 *
 * @author tav
 */
public class JBCefJSQuery implements JBCefDisposable {
  @NotNull private final JSQueryFunc myFunc;
  @NotNull private final CefClient myCefClient;
  @NotNull private final DisposeHelper myDisposeHelper = new DisposeHelper();

  @NotNull private final Map<Function<String, Response>, CefMessageRouterHandler> myHandlerMap = Collections.synchronizedMap(new HashMap<>());

  private JBCefJSQuery(@NotNull JBCefBrowser browser, @NotNull JBCefJSQuery.JSQueryFunc func) {
    myFunc = func;
    myCefClient = browser.getJBCefClient().getCefClient();
    Disposer.register(browser, this);
  }

  /**
   * Creates a unique JS query.
   *
   * @see JBCefClient#JBCEFCLIENT_JSQUERY_POOL_SIZE_PROP
   * @param browser the associated cef browser
   */
  public static JBCefJSQuery create(@NotNull JBCefBrowser browser) {
    Function<Void, JBCefJSQuery> create = (v) -> {
      return new JBCefJSQuery(browser, new JSQueryFunc(browser.getJBCefClient(), browser.getJSQueryCounter(), false));
    };
    if (!browser.isCefBrowserCreated()) {
      return create.apply(null);
    }
    JBCefClient.JSQueryPool pool = browser.getJBCefClient().getJSQueryPool();
    JSQueryFunc slot;
    if (pool != null && (slot = pool.getFreeSlot()) != null) {
      return new JBCefJSQuery(browser, slot);
    }
    // this query will produce en error in JS debug console
    return create.apply(null);
  }

  /**
   * Returns the query callback to inject into JS code
   *
   * @param queryResult the result (JS variable name or JS value) that will be passed to the java handler {@link #addHandler(Function)}
   */
  public String inject(@Nullable String queryResult) {
    return inject(queryResult, "function(response) {}", "function(error_code, error_message) {}");
  }

  /**
   * Returns the query callback to inject into JS code
   *
   * @param queryResult the result (JS variable name or JS value) that will be passed to the java handler {@link #addHandler(Function)}
   * @param onSuccessCallback JS callback in format: function(response) {}
   * @param onFailureCallback JS callback in format: function(error_code, error_message) {}
   */
  public String inject(@Nullable String queryResult, @NotNull String onSuccessCallback, @NotNull String onFailureCallback) {
    return "window." + myFunc.myFuncName +
           "({request: '' + " + queryResult + "," +
             "onSuccess: " + onSuccessCallback + "," +
             "onFailure: " + onFailureCallback +
           "});";
  }

  public void addHandler(@NotNull Function<String, Response> handler) {
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
        }
        return true;
      }
    }, true);
    myHandlerMap.put(handler, cefHandler);
  }

  public void removeHandler(@NotNull Function<String, Response> handler) {
    CefMessageRouterHandler cefHandler = myHandlerMap.remove(handler);
    if (cefHandler != null) {
      myFunc.myRouter.removeHandler(cefHandler);
    }
  }

  @Override
  public void dispose() {
    myDisposeHelper.dispose(() -> {
      myCefClient.removeMessageRouter(myFunc.myRouter);
      myHandlerMap.clear();
    });
  }

  @Override
  public boolean isDisposed() {
    return myDisposeHelper.isDisposed();
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
    final CefMessageRouter myRouter;
    final String myFuncName;

    JSQueryFunc(@NotNull JBCefClient client, int index, boolean isSlot) {
      myFuncName = "cefQuery_" + client.hashCode() + "_" + (isSlot ? "slot_" : "") + index;
      CefMessageRouter.CefMessageRouterConfig config = new CefMessageRouter.CefMessageRouterConfig();
      config.jsQueryFunction = myFuncName;
      config.jsCancelFunction = myFuncName;
      myRouter = CefMessageRouter.create(config);
      client.getCefClient().addMessageRouter(myRouter);
    }
  }
}
