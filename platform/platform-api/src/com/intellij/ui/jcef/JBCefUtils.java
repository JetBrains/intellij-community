// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.util.containers.hash.HashMap;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Function;

/**
 * @author tav
 */
public class JBCefUtils {
  private static final Map<String, CefMessageRouter> id2msgRouter = new HashMap<>();

  public static void addJSHandler(@NotNull CefClient cefClient, @NotNull String jsRequestID, @NotNull Function<String, Boolean> handler) {
    CefMessageRouter.CefMessageRouterConfig config = new CefMessageRouter.CefMessageRouterConfig();
    config.jsQueryFunction = jsRequestID;
    config.jsCancelFunction = jsRequestID;
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
        return handler.apply(request);
      }
    }, true);
    cefClient.addMessageRouter(msgRouter);
    id2msgRouter.put(jsRequestID, msgRouter);
  }

  public static void removeJSHandler(@NotNull CefClient cefClient, @NotNull String jsRequestID) {
    CefMessageRouter r = id2msgRouter.get(jsRequestID);
    if (r != null) {
      cefClient.removeMessageRouter(r);
    }
  }

  public static String makeJSRequestCode(@NotNull String id, @Nullable String request) {
    return "window." + id + "({request: '' + " + request + "});";
  }

  public static String makeUniqueJSRequestID(@NotNull Class<?> hoster, @NotNull String requestLocalID) {
    return "cefQuery_" + hoster.hashCode() + "_" + requestLocalID;
  }
}
