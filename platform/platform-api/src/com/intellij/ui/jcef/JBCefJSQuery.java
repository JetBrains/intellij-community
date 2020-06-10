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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * A JS query callback.
 *
 * @author tav
 */
public class JBCefJSQuery implements JBCefDisposable {
  @NotNull private final String myJSCallID;
  @NotNull private final CefMessageRouter myMsgRouter;
  @NotNull private final CefClient myCefClient;
  @NotNull private final DisposeHelper myDisposeHelper = new DisposeHelper();

  @NotNull private final Map<Function<String, Response>, CefMessageRouterHandler> myHandlerMap = Collections.synchronizedMap(new HashMap<>());

  @NotNull private static final AtomicInteger UNIQUE_ID_COUNTER = new AtomicInteger(0);

  private JBCefJSQuery(@NotNull JBCefBrowser browser, @NotNull String jsCallID) {
    myJSCallID = jsCallID;
    CefMessageRouter.CefMessageRouterConfig config = new CefMessageRouter.CefMessageRouterConfig();
    config.jsQueryFunction = myJSCallID;
    config.jsCancelFunction = myJSCallID;
    myMsgRouter = CefMessageRouter.create(config);
    myCefClient = browser.getJBCefClient().getCefClient();
    myCefClient.addMessageRouter(myMsgRouter);
    Disposer.register(browser, this);
  }

  /**
   * Creates a unique JS query
   *
   * @param browser the associated cef browser
   */
  public static JBCefJSQuery create(@NotNull JBCefBrowser browser) {
    return new JBCefJSQuery(browser, "cefQuery_" + browser.hashCode() + "_" + UNIQUE_ID_COUNTER.incrementAndGet());
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
    return "window." + myJSCallID +
           "({request: '' + " + queryResult + "," +
             "onSuccess: " + onSuccessCallback + "," +
             "onFailure: " + onFailureCallback +
           "});";
  }

  public void addHandler(@NotNull Function<String, Response> handler) {
    CefMessageRouterHandler cefHandler;
    myMsgRouter.addHandler(cefHandler = new CefMessageRouterHandlerAdapter() {
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
      myMsgRouter.removeHandler(cefHandler);
    }
  }

  @Override
  public void dispose() {
    myDisposeHelper.dispose(() -> {
      myCefClient.removeMessageRouter(myMsgRouter);
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
}
