// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A JS query callback.
 *
 * @author tav
 */
@ApiStatus.Experimental
public class JBCefJSQuery {
  @NotNull private final String myJSCallID;

  private static final AtomicInteger ID_COUNTER = new AtomicInteger(0);

  private static final Map<JBCefJSQuery, CefMessageRouter> ourMsgRouterMap = new HashMap<>();

  private JBCefJSQuery(@NotNull String jsCallID) {
    myJSCallID = jsCallID;
  }

  /**
   * Creates a unique JS query
   *
   * @param queryName an arbitrary query name (mostly serves debugging purposes)
   */
  public static JBCefJSQuery create(@NotNull String queryName) {
    return new JBCefJSQuery("cefQuery_" + queryName + "_" + ID_COUNTER.incrementAndGet());
  }

  /**
   * Returns query callback call to inject into JS code
   *
   * @param queryResult the result passed to the handler {@link #addHandler(JBCefClient, Function)}
   */
  public String inject(@Nullable String queryResult) {
    return inject(queryResult, "function(response) {}", "function(error_code, error_message) {}");
  }

  /**
   * Returns query callback call to inject into JS code
   *
   * @param queryResult the result passed to the handler {@link #addHandler(JBCefClient, Function)}
   * @param onSuccessCallback JS callback in format: function(response) {}
   * @param onFailureCallback JS callback in format: function(error_code, error_message) {}
   */
  public String inject(@Nullable String queryResult,
                       @SuppressWarnings("unused") @NotNull String onSuccessCallback,
                       @SuppressWarnings("unused") @NotNull String onFailureCallback)
  {
    return "window." + myJSCallID +
           "({request: '' + " + queryResult + "," +
             "onSuccess: " + onSuccessCallback + "," +
             "onFailure: " + onFailureCallback +
           "});";
  }

  public void addHandler(@NotNull JBCefClient cefClient, @NotNull Consumer<String> handler) {
    addHandler(cefClient, result -> {
      handler.accept(result);
      return null;
    });
  }

  public void addHandler(@NotNull JBCefClient cefClient, @NotNull Function<String, Response> handler) {
    CefMessageRouter.CefMessageRouterConfig config = new CefMessageRouter.CefMessageRouterConfig();
    config.jsQueryFunction = myJSCallID;
    config.jsCancelFunction = myJSCallID;
    CefMessageRouter msgRouter = CefMessageRouter.create(config);
    msgRouter.addHandler(new CefMessageRouterHandlerAdapter() {
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
    cefClient.getCefClient().addMessageRouter(msgRouter);
    ourMsgRouterMap.put(this, msgRouter);
  }

  public void removeHandler(@NotNull JBCefClient cefClient) {
    CefMessageRouter r = ourMsgRouterMap.get(this);
    if (r != null) {
      cefClient.getCefClient().removeMessageRouter(r);
      ourMsgRouterMap.remove(this);
    }
  }

  /**
   * A JS handler response.
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
      this.myResponse = response;
      this.myErrCode = errCode;
      this.myErrMsg = errMsg;
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
