// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// This is a slightly modified version of test 'tests.detailed.MainFrame' from repository https://github.com/JetBrains/jcef.git
package com.intellij.internal.jcef.test.detailed.handler;

import org.cef.browser.CefBrowser;
import org.cef.handler.CefKeyboardHandlerAdapter;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class  KeyboardHandler extends CefKeyboardHandlerAdapter {
    @Override
    public boolean onKeyEvent(CefBrowser browser, CefKeyEvent event) {
        if (!event.focus_on_editable_field && event.windows_key_code == 0x20) {
            // Special handling for the space character when an input element does not
            // have focus. Handling the event in OnPreKeyEvent() keeps the event from
            // being processed in the renderer. If we instead handled the event in the
            // OnKeyEvent() method the space key would cause the window to scroll in
            // addition to showing the alert box.
            if (event.type == CefKeyEvent.EventType.KEYEVENT_RAWKEYDOWN) {
                browser.executeJavaScript("alert('You pressed the space bar!');", "", 0);
            }
            return true;
        } else if (event.type == CefKeyEvent.EventType.KEYEVENT_RAWKEYDOWN && event.is_system_key) {
            // CMD+[key] is not working on a Mac.
            // This switch statement delegates the common keyboard shortcuts to the browser
            switch (event.unmodified_character) {
                case 'a':
                    browser.getFocusedFrame().selectAll();
                    break;
                case 'c':
                    browser.getFocusedFrame().copy();
                    break;
                case 'v':
                    browser.getFocusedFrame().paste();
                    break;
                case 'x':
                    browser.getFocusedFrame().cut();
                    break;
                case 'z':
                    browser.getFocusedFrame().undo();
                    break;
                case 'Z':
                    browser.getFocusedFrame().redo();
                    break;
                default:
                    return false;
            }
            return true;
        }
        return false;
    }
}
