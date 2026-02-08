// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// This is a slightly modified version of test 'tests.detailed.MainFrame' from repository https://github.com/JetBrains/jcef.git
package com.intellij.internal.jcef.test.detailed.handler;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class  MessageRouterHandler extends CefMessageRouterHandlerAdapter {
    @Override
    public boolean onQuery(CefBrowser browser, CefFrame frame, long query_id, String request,
            boolean persistent, CefQueryCallback callback) {
        if (request.indexOf("BindingTest:") == 0) {
            // Reverse the message and return it to the JavaScript caller.
            String msg = request.substring(12);
            callback.success(new StringBuilder(msg).reverse().toString());
            return true;
        }
        // Not handled.
        return false;
    }
}
