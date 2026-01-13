// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// This is a slightly modified version of test 'tests.detailed.MainFrame' from repository https://github.com/JetBrains/jcef.git
package com.intellij.internal.jcef.test.detailed;

import org.cef.browser.CefBrowser;
import org.cef.handler.CefLifeSpanHandlerAdapter;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

@ApiStatus.Internal
public class  BrowserFrame extends JFrame {
  private volatile boolean isClosed_ = false;
  private CefBrowser browser_ = null;
  private Runnable afterParentChangedAction_ = null;

  public BrowserFrame() {
    this(null);
  }

  public BrowserFrame(String title) {
    super(title);

    // Browser window closing works as follows:
    //   1. Clicking the window X button calls WindowAdapter.windowClosing.
    //   2. WindowAdapter.windowClosing calls CefBrowser.close(false).
    //   3. CEF calls CefLifeSpanHandler.doClose() which calls CefBrowser.doClose()
    //      which returns true (canceling the close).
    //   4. CefBrowser.doClose() triggers another call to WindowAdapter.windowClosing.
    //   5. WindowAdapter.windowClosing calls CefBrowser.close(true).
    //   6. For windowed browsers CEF destroys the native window handle. For OSR
    //      browsers CEF calls CefLifeSpanHandler.doClose() which calls
    //      CefBrowser.doClose() again which returns false (allowing the close).
    //   7. CEF calls CefLifeSpanHandler.onBeforeClose and the browser is destroyed.
    //
    // On macOS pressing Cmd+Q results in a call to CefApp.handleBeforeTerminate
    // which calls CefBrowser.close(true) for each existing browser. CEF then calls
    // CefLifeSpanHandler.onBeforeClose and the browser is destroyed.
    //
    // Application shutdown works as follows:
    //   1. CefLifeSpanHandler.onBeforeClose calls CefApp.getInstance().dispose()
    //      when the last browser window is destroyed.
    //   2. CefAppHandler.stateHasChanged terminates the application by calling
    //      System.exit(0) when the state changes to CefAppState.TERMINATED.
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        if (browser_ == null) {
          // If there's no browser we can dispose immediately.
          isClosed_ = true;
          dispose();
          return;
        }

        boolean isClosed = isClosed_;

        if (isClosed) {
          // Cause browser.doClose() to return false so that OSR browsers
          // can close.
          browser_.setCloseAllowed();
        }

        // Results in another call to this method.
        browser_.close(isClosed);
        if (!isClosed_) {
          isClosed_ = true;
        }
        if (isClosed) {
          // Dispose after the 2nd call to this method.
          dispose();
        }
      }
    });
  }

  public void setBrowser(CefBrowser browser) {
    if (browser_ == null) browser_ = browser;

    browser_.getClient().removeLifeSpanHandler();
    browser_.getClient().addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
      @Override
      public void onAfterParentChanged(CefBrowser browser) {
        if (afterParentChangedAction_ != null) {
          SwingUtilities.invokeLater(afterParentChangedAction_);
          afterParentChangedAction_ = null;
        }
      }

      @Override
      public boolean doClose(CefBrowser browser) {
        boolean result = browser.doClose();
        return result;
      }
    });
  }

  public void removeBrowser(Runnable r) {
    afterParentChangedAction_ = r;
    remove(browser_.getUIComponent());
    // The removeNotify() notification should be sent as a result of calling remove().
    // However, it isn't in all cases so we do it manually here.
    browser_.getUIComponent().removeNotify();
    browser_ = null;
  }

  public CefBrowser getBrowser() {
    return browser_;
  }
}
