// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// This is a slightly modified version of test 'tests.detailed.MainFrame' from repository https://github.com/JetBrains/jcef.git
package com.intellij.internal.jcef.test.detailed;

import com.intellij.internal.jcef.test.detailed.dialog.DownloadDialog;
import com.intellij.internal.jcef.test.detailed.handler.*;
import com.intellij.internal.jcef.test.detailed.ui.ControlPanel;
import com.intellij.internal.jcef.test.detailed.ui.MenuBar;
import com.intellij.internal.jcef.test.detailed.ui.StatusPanel;
import com.intellij.internal.jcef.test.detailed.util.DataUri;
import com.intellij.ui.jcef.JBCefOSRHandlerFactory;
import org.cef.CefApp;
import org.cef.CefBrowserSettings;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.browser.CefRendering;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefFocusHandlerAdapter;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.handler.CefRenderHandler;
import org.cef.network.CefCookieManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.lang.reflect.Method;
import java.util.function.Supplier;

// This is a slightly modified version of test class 'tests.detailed.MainFrame' from repository https://github.com/JetBrains/jcef.git
@SuppressWarnings("ALL")
@ApiStatus.Internal
public class  MainFrame extends BrowserFrame {
  public static int queryCounter;

  private final CefApp cefApp_;
  private String errorMsg_ = "";
  private ControlPanel control_pane_;
  private StatusPanel status_panel_;
  private boolean browserFocus_ = true;
  private final JPanel contentPanel_;
  private JFrame fullscreenFrame_;

  public MainFrame(@NotNull CefApp cefApp) {
    this(cefApp, false, 0);
  }

  public MainFrame(@NotNull CefApp cefApp, boolean createImmediately, int windowless_frame_rate) {
    cefApp_ = cefApp;
    //    By calling the method createClient() the native part
    //    of JCEF/CEF will be initialized and an  instance of
    //    CefClient will be created. You can create one to many
    //    instances of CefClient.
    CefClient client_ = cefApp.createClient();

    // 2) You have the ability to pass different handlers to your
    //    instance of CefClient. Each handler is responsible to
    //    deal with different informations (e.g. keyboard input).
    //
    //    For each handler (with more than one method) adapter
    //    classes exists. So you don't need to override methods
    //    you're not interested in.
    DownloadDialog downloadDialog = new DownloadDialog(this);
    client_.addContextMenuHandler(new ContextMenuHandler(this));
    client_.addDownloadHandler(downloadDialog);
    client_.addDragHandler(new DragHandler());
    client_.addJSDialogHandler(new JSDialogHandler());
    client_.addKeyboardHandler(new KeyboardHandler());
    client_.addRequestHandler(new RequestHandler(this));

    //    Beside the normal handler instances, we're registering a MessageRouter
    //    as well. That gives us the opportunity to reply to JavaScript method
    //    calls (JavaScript binding). We're using the default configuration, so
    //    that the JavaScript binding methods "cefQuery" and "cefQueryCancel"
    //    are used.
    CefMessageRouter.CefMessageRouterConfig config = new CefMessageRouter.CefMessageRouterConfig();
    config.jsQueryFunction = "cef_query_" + (++queryCounter);
    config.jsCancelFunction = "cef_query_cancel_" + queryCounter;
    CefMessageRouter msgRouter = CefMessageRouter.create(config);
    msgRouter.addHandler(new MessageRouterHandler(), true);
    msgRouter.addHandler(new MessageRouterHandlerEx(client_), false);
    client_.addMessageRouter(msgRouter);

    // 2.1) We're overriding CefDisplayHandler as nested anonymous class
    //      to update our address-field, the title of the panel as well
    //      as for updating the status-bar on the bottom of the browser
    client_.addDisplayHandler(new CefDisplayHandlerAdapter() {
      @Override
      public void onAddressChange(CefBrowser browser, CefFrame frame, String url) {
        control_pane_.setAddress(browser, url);
      }

      @Override
      public void onTitleChange(CefBrowser browser, String title) {
        setTitle(title);
      }

      @Override
      public void onStatusMessage(CefBrowser browser, String value) {
        status_panel_.setStatusText(value);
      }

      @Override
      public void onFullscreenModeChange(CefBrowser browser, boolean fullscreen) {
        setBrowserFullscreen(fullscreen);
      }
    });

    // 2.2) To disable/enable navigation buttons and to display a prgress bar
    //      which indicates the load state of our website, we're overloading
    //      the CefLoadHandler as nested anonymous class. Beside this, the
    //      load handler is responsible to deal with (load) errors as well.
    //      For example if you navigate to a URL which does not exist, the
    //      browser will show up an error message.
    client_.addLoadHandler(new CefLoadHandlerAdapter() {
      @Override
      public void onLoadingStateChange(CefBrowser browser, boolean isLoading,
                                       boolean canGoBack, boolean canGoForward) {
        SwingUtilities.invokeLater(() -> {
          control_pane_.update(browser, isLoading, canGoBack, canGoForward);
          status_panel_.setIsInProgress(isLoading);

          if (!isLoading && !errorMsg_.isEmpty()) {
            browser.loadURL(DataUri.create("text/html", errorMsg_));
            errorMsg_ = "";
          }
        });
      }

      @Override
      public void onLoadError(CefBrowser browser, CefFrame frame, ErrorCode errorCode,
                              String errorText, String failedUrl) {
        if (errorCode != ErrorCode.ERR_NONE && errorCode != ErrorCode.ERR_ABORTED
            && frame == browser.getMainFrame()) {
          errorMsg_ = "<html><head>";
          errorMsg_ += "<title>Error while loading</title>";
          errorMsg_ += "</head><body>";
          errorMsg_ += "<h1>" + errorCode + "</h1>";
          errorMsg_ += "<h3>Failed to load " + failedUrl + "</h3>";
          errorMsg_ += "<p>" + (errorText == null ? "" : errorText) + "</p>";
          errorMsg_ += "</body></html>";
          browser.stopLoad();
        }
      }
    });

    CefBrowserSettings browserSettings = new CefBrowserSettings();
    browserSettings.windowless_frame_rate = windowless_frame_rate;

    // Create the browser.
    final String startURL = "http://www.google.com";
    CefBrowser browser;
    if (CefApp.isRemoteEnabled()) {
      final Supplier<CefRendering> defaultRenderingFactory = () -> {
        JBCefOSRHandlerFactory osrHandlerFactory = JBCefOSRHandlerFactory.getInstance();
        JComponent component = osrHandlerFactory.createComponent(true);
        CefRenderHandler handler = osrHandlerFactory.createCefRenderHandler(component);
        return new CefRendering.CefRenderingWithHandler(handler, component);
      };
      browser = client_.createBrowser(startURL, defaultRenderingFactory, false, null, null);
      try {
        Method m = browser.getUIComponent().getClass().getMethod("setBrowser", CefBrowser.class);
        m.setAccessible(true);
        m.invoke(browser.getUIComponent(), browser);
      }
      catch (ReflectiveOperationException e) {
        // e.printStackTrace();
      }
    }
    else {
      browser = client_.createBrowser(startURL, true, true, null, browserSettings);
    }

    setBrowser(browser);

    // Set up the UI for this example implementation.
    contentPanel_ = createContentPanel();
    getContentPane().add(contentPanel_, BorderLayout.CENTER);

    // Clear focus from the browser when the address field gains focus.
    control_pane_.getAddressField().addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        if (!browserFocus_) return;
        browserFocus_ = false;
        KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();
        control_pane_.getAddressField().requestFocus();
      }
    });

    // Clear focus from the address field when the browser gains focus.
    client_.addFocusHandler(new CefFocusHandlerAdapter() {
      @Override
      public void onGotFocus(CefBrowser browser) {
        if (browserFocus_) return;
        browserFocus_ = true;
        KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();
        browser.setFocus(true);
      }

      @Override
      public void onTakeFocus(CefBrowser browser, boolean next) {
        browserFocus_ = false;
      }
    });

    if (createImmediately) browser.createImmediately();

    // Add the browser to the UI.
    contentPanel_.add(getBrowser().getUIComponent(), BorderLayout.CENTER);

    MenuBar menuBar = new MenuBar(
      this, browser, control_pane_, downloadDialog, CefCookieManager.getGlobalManager());

    menuBar.addBookmark("Binding Test", "client://tests/binding_test.html");
    menuBar.addBookmark("Binding Test 2", "client://tests/binding_test2.html");
    menuBar.addBookmark("Download Test", "https://cef-builds.spotifycdn.com/index.html");
    menuBar.addBookmark("Login Test (username:pumpkin, password:pie)",
                        "http://www.colostate.edu/~ric/protect/your.html");
    menuBar.addBookmark("Certificate-error Test", "https://www.k2go.de");
    menuBar.addBookmark("Resource-Handler Test", "http://www.foo.bar/");
    menuBar.addBookmark("Resource-Handler Set Error Test", "http://seterror.test/");
    menuBar.addBookmark(
      "Scheme-Handler Test 1: (scheme \"client\")", "client://tests/handler.html");
    menuBar.addBookmark(
      "Scheme-Handler Test 2: (scheme \"search\")", "search://do a barrel roll/");
    menuBar.addBookmark("Spellcheck Test", "client://tests/spellcheck.html");
    menuBar.addBookmark("LocalStorage Test", "client://tests/localstorage.html");
    menuBar.addBookmark("Transparency Test", "client://tests/transparency.html");
    menuBar.addBookmark("Fullscreen Test",
                        "https://www.w3schools.com/howto/tryit.asp?filename=tryhow_js_fullscreen2");
    menuBar.addBookmarkSeparator();
    menuBar.addBookmark(
      "javachromiumembedded", "https://bitbucket.org/chromiumembedded/java-cef");
    menuBar.addBookmark("chromiumembedded", "https://bitbucket.org/chromiumembedded/cef");
    setJMenuBar(menuBar);
  }

  public @NotNull CefApp getCefApp() { return cefApp_; }

  private JPanel createContentPanel() {
    JPanel contentPanel = new JPanel(new BorderLayout());
    control_pane_ = new ControlPanel(getBrowser());
    status_panel_ = new StatusPanel();
    contentPanel.add(control_pane_, BorderLayout.NORTH);
    contentPanel.add(status_panel_, BorderLayout.SOUTH);
    return contentPanel;
  }

  public void setBrowserFullscreen(boolean fullscreen) {
    SwingUtilities.invokeLater(() -> {
      Component browserUI = getBrowser().getUIComponent();
      if (fullscreen) {
        if (fullscreenFrame_ == null) {
          fullscreenFrame_ = new JFrame();
          fullscreenFrame_.setUndecorated(true);
          fullscreenFrame_.setResizable(true);
        }
        GraphicsConfiguration gc = this.getGraphicsConfiguration();
        fullscreenFrame_.setBounds(gc.getBounds());
        gc.getDevice().setFullScreenWindow(fullscreenFrame_);

        contentPanel_.remove(browserUI);
        fullscreenFrame_.add(browserUI);
        fullscreenFrame_.setVisible(true);
        fullscreenFrame_.validate();
      }
      else {
        fullscreenFrame_.remove(browserUI);
        fullscreenFrame_.setVisible(false);
        contentPanel_.add(browserUI, BorderLayout.CENTER);
        contentPanel_.validate();
      }
    });
  }
}
