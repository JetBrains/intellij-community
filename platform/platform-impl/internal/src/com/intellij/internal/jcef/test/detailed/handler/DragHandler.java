// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// This is a slightly modified version of test 'tests.detailed.MainFrame' from repository https://github.com/JetBrains/jcef.git
package com.intellij.internal.jcef.test.detailed.handler;

import org.cef.browser.CefBrowser;
import org.cef.callback.CefDragData;
import org.cef.handler.CefDragHandler;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class  DragHandler implements CefDragHandler {
    @Override
    public boolean onDragEnter(CefBrowser browser, CefDragData dragData, int mask) {
        return false;
    }
}
