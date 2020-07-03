// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.ui.JBColor;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefContextMenuParams;
import org.cef.callback.CefMenuModel;
import org.cef.handler.*;
import org.cef.misc.BoolRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static com.intellij.ui.jcef.JBCefEventUtils.*;
import static org.cef.callback.CefMenuModel.MenuId.MENU_ID_USER_LAST;

/**
 * A wrapper over {@link CefBrowser}.
 * <p>
 * Use {@link #getComponent()} as the browser's UI component.
 * Use {@link #loadURL(String)} or {@link #loadHTML(String)} for loading.
 *
 * @author tav
 */
public class JBCefBrowser implements JBCefDisposable {
  private static final String BLANK_URI = "about:blank";

  private static final String JBCEFBROWSER_INSTANCE_PROP = "JBCefBrowser.instance";

  @NotNull private static final List<Consumer<JBCefBrowser>> ourOnBrowserMoveResizeCallbacks =
    Collections.synchronizedList(new ArrayList<>(1));

  @NotNull private final JBCefClient myCefClient;
  @NotNull private final JPanel myComponent;
  @NotNull private final CefBrowser myCefBrowser;
  @Nullable private volatile JBCefCookieManager myJBCefCookieManager;
  @NotNull private final CefFocusHandler myCefFocusHandler;
  @Nullable private final CefLifeSpanHandler myLifeSpanHandler;
  @NotNull private final DisposeHelper myDisposeHelper = new DisposeHelper();

  private final AtomicInteger myJSQueryCounter = new AtomicInteger(0);
  private final boolean myIsDefaultClient;
  private volatile boolean myIsCefBrowserCreated;
  @Nullable private volatile LoadDeferrer myLoadDeferrer;
  private JDialog myDevtoolsFrame = null;
  protected CefContextMenuHandler myDefaultContextMenuHandler;
  private final ReentrantLock myCookieManagerLock = new ReentrantLock();

  private static class LoadDeferrer {
    @Nullable private final String myHtml;
    @NotNull private final String myUrl;

    private LoadDeferrer(@Nullable String html, @NotNull String url) {
      myHtml = html;
      myUrl = url;
    }

    @NotNull
    public static LoadDeferrer urlDeferrer(String url) {
      return new LoadDeferrer(null, url);
    }

    @NotNull
    public static LoadDeferrer htmlDeferrer(String html, String url) {
      return new LoadDeferrer(html, url);
    }

    public void load(@NotNull CefBrowser browser) {
      // JCEF demands async loading.
      SwingUtilities.invokeLater(
        myHtml == null ?
          () -> browser.loadURL(myUrl) :
          () -> loadString(browser, myHtml, myUrl));
    }
  }

  /**
   * Creates a browser with the provided {@code JBCefClient} and initial URL. The client's lifecycle is the responsibility of the caller.
   */
  public JBCefBrowser(@NotNull JBCefClient client, @Nullable String url) {
    this(client, false, url);
  }

  public JBCefBrowser(@NotNull CefBrowser cefBrowser, @NotNull JBCefClient client) {
    this(cefBrowser, client, false, null);
  }

  private JBCefBrowser(@NotNull JBCefClient client, boolean isDefaultClient, @Nullable String url) {
    this(null, client, isDefaultClient, url);
  }

  private JBCefBrowser(@Nullable CefBrowser cefBrowser, @NotNull JBCefClient client, boolean isDefaultClient, @Nullable String url) {
    if (client.isDisposed()) {
      throw new IllegalArgumentException("JBCefClient is disposed");
    }
    myCefClient = client;
    myIsDefaultClient = isDefaultClient;

    myComponent = SystemInfoRt.isWindows ?
      new JPanel(new BorderLayout()) {
        @Override
        public void removeNotify() {
          if (myCefBrowser.getUIComponent().hasFocus()) {
            // pass focus before removal
            myCefBrowser.setFocus(false);
          }
          super.removeNotify();
        }
      } :
      new JPanel(new BorderLayout());

    myComponent.setBackground(JBColor.background());

    myCefBrowser = cefBrowser != null ?
      cefBrowser : myCefClient.getCefClient().createBrowser(url != null ? url : BLANK_URI, false, false);
    JComponent uiComp = (JComponent)myCefBrowser.getUIComponent();
    uiComp.putClientProperty(JBCEFBROWSER_INSTANCE_PROP, this);
    myComponent.add(uiComp, BorderLayout.CENTER);

    myComponent.setFocusCycleRoot(true);
    myComponent.setFocusTraversalPolicyProvider(true);
    myComponent.setFocusTraversalPolicy(new MyFTP());

    myComponent.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        ourOnBrowserMoveResizeCallbacks.forEach(callback -> callback.accept(JBCefBrowser.this));
      }

      @Override
      public void componentMoved(ComponentEvent e) {
        ourOnBrowserMoveResizeCallbacks.forEach(callback -> callback.accept(JBCefBrowser.this));
      }

      @Override
      public void componentShown(ComponentEvent e) {
        ourOnBrowserMoveResizeCallbacks.forEach(callback -> callback.accept(JBCefBrowser.this));
      }

      @Override
      public void componentHidden(ComponentEvent e) {
        ourOnBrowserMoveResizeCallbacks.forEach(callback -> callback.accept(JBCefBrowser.this));
      }
    });

    if (cefBrowser == null) {
      myCefClient.addLifeSpanHandler(myLifeSpanHandler = new CefLifeSpanHandlerAdapter() {
          @Override
          public void onAfterCreated(CefBrowser browser) {
            myIsCefBrowserCreated = true;
            myCefClient.notifyBrowserCreated(JBCefBrowser.this);
            LoadDeferrer loader = myLoadDeferrer;
            if (loader != null) {
              loader.load(browser);
              myLoadDeferrer = null;
            }
          }
        }, myCefBrowser);
    }
    else {
      myLifeSpanHandler = null;
    }

    myCefClient.addFocusHandler(myCefFocusHandler = new CefFocusHandlerAdapter() {
      @Override
      public boolean onSetFocus(CefBrowser browser, FocusSource source) {
        if (source == FocusSource.FOCUS_SOURCE_NAVIGATION) {
          if (SystemInfoRt.isWindows) {
            myCefBrowser.setFocus(false);
          }
          return true; // suppress focusing the browser on navigation events
        }
        if (SystemInfoRt.isLinux) {
          browser.getUIComponent().requestFocus();
        }
        else {
          browser.getUIComponent().requestFocusInWindow();
        }
        return false;
      }
    }, myCefBrowser);

    if (SystemInfoRt.isWindows) {
      myCefBrowser.getUIComponent().addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          if (myCefBrowser.getUIComponent().isFocusable()) {
            myCefBrowser.setFocus(true);
          }
        }
      });
    }

    myCefClient.addKeyboardHandler(new CefKeyboardHandlerAdapter() {
      @Override
      public boolean onPreKeyEvent(CefBrowser browser, CefKeyEvent cefKeyEvent, BoolRef is_keyboard_shortcut) {
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        boolean consume = focusOwner != browser.getUIComponent();
        if (consume && SystemInfoRt.isMac && isUpDownKeyEvent(cefKeyEvent)) return true; // consume

        Window focusedWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
        KeyEvent javaKeyEvent = convertCefKeyEvent(cefKeyEvent, focusedWindow);
        Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(javaKeyEvent);

        if (javaKeyEvent.getID() == KeyEvent.KEY_PRESSED && cefKeyEvent.modifiers == 0 && cefKeyEvent.character != 0) {
          javaKeyEvent = javaKeyEventWithID(javaKeyEvent, KeyEvent.KEY_TYPED);
          Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(javaKeyEvent);
        }
        return consume;
      }
    }, myCefBrowser);

    myDefaultContextMenuHandler = createDefaultContextMenuHandler();
    myCefClient.addContextMenuHandler(myDefaultContextMenuHandler, this.getCefBrowser());
  }

  protected DefaultCefContextMenuHandler createDefaultContextMenuHandler() {
    boolean isInternal = ApplicationManager.getApplication().isInternal();
    return new DefaultCefContextMenuHandler(isInternal);
  }

  /**
   * Loads URL.
   */
  public void loadURL(@NotNull String url) {
    if (myIsCefBrowserCreated) {
      myCefBrowser.loadURL(url);
    }
    else {
      myLoadDeferrer = LoadDeferrer.urlDeferrer(url);
    }
  }

  /**
   * Loads html content.
   *
   * @param html content to load
   * @param url a dummy URL that may affect restriction policy applied to the content
   */
  public void loadHTML(@NotNull String html, @NotNull String url) {
    if (myIsCefBrowserCreated) {
      loadString(myCefBrowser, html, url);
    }
    else {
      myLoadDeferrer = LoadDeferrer.htmlDeferrer(html, url);
    }
  }

  /**
   * Loads html content.
   */
  public void loadHTML(@NotNull String html) {
    loadHTML(html, BLANK_URI);
  }

  private static void loadString(CefBrowser cefBrowser, String html, String url) {
    url = JBCefFileSchemeHandlerFactory.registerLoadHTMLRequest(cefBrowser, html, url);
    cefBrowser.loadURL(url);
  }

  /**
   * Creates a browser with default {@link JBCefClient}. The default client is disposed with this browser and may not be used with other browsers.
   */
  @SuppressWarnings("unused")
  public JBCefBrowser() {
    this(JBCefApp.getInstance().createClient(), true, null);
  }

  /**
   * @see #JBCefBrowser()
   * @param url initial url
   */
  @SuppressWarnings("unused")
  public JBCefBrowser(@NotNull String url) {
    this(JBCefApp.getInstance().createClient(), true, url);
  }

  @NotNull
  public JComponent getComponent() {
    return myComponent;
  }

  @NotNull
  public CefBrowser getCefBrowser() {
    return myCefBrowser;
  }

  @NotNull
  public JBCefClient getJBCefClient() {
    return myCefClient;
  }

  @NotNull
  public JBCefCookieManager getJBCefCookieManager() {
    myCookieManagerLock.lock();
    try {
      if (myJBCefCookieManager == null) {
        myJBCefCookieManager = new JBCefCookieManager();
      }
      return myJBCefCookieManager;
    }
    finally {
      myCookieManagerLock.unlock();
    }
  }

  @SuppressWarnings("unused")
  public void setJBCefCookieManager(@NotNull JBCefCookieManager jBCefCookieManager) {
    myCookieManagerLock.lock();
    try {
      myJBCefCookieManager = jBCefCookieManager;
    }
    finally {
      myCookieManagerLock.unlock();
    }
  }

  @Nullable
  private static Window getActiveFrame() {
    for (Frame frame : Frame.getFrames()) {
      if (frame.isActive()) return frame;
    }
    return null;
  }

  public void openDevtools() {
    if (myDevtoolsFrame != null) {
      myDevtoolsFrame.toFront();
      return;
    }

    Window activeFrame = getActiveFrame();
    if (activeFrame == null) return;
    Rectangle bounds = activeFrame.getGraphicsConfiguration().getBounds();

    myDevtoolsFrame = new JDialog(activeFrame);
    myDevtoolsFrame.setTitle("JCEF DevTools");
    myDevtoolsFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    myDevtoolsFrame.setBounds(bounds.width / 4 + 100, bounds.height / 4 + 100, bounds.width / 2, bounds.height / 2);
    myDevtoolsFrame.setLayout(new BorderLayout());
    JBCefBrowser devTools = new JBCefBrowser(myCefBrowser.getDevTools(), myCefClient);
    myDevtoolsFrame.add(devTools.getComponent(), BorderLayout.CENTER);
    myDevtoolsFrame.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosed(WindowEvent e) {
        myDevtoolsFrame = null;
        Disposer.dispose(devTools);
      }
    });
    myDevtoolsFrame.setVisible(true);
  }

  @Override
  public void dispose() {
    myDisposeHelper.dispose(() -> {
      myCefClient.removeFocusHandler(myCefFocusHandler, myCefBrowser);
      if (myLifeSpanHandler != null) myCefClient.removeLifeSpanHandler(myLifeSpanHandler, myCefBrowser);
      myCefBrowser.stopLoad();
      myCefBrowser.close(false);
      if (myIsDefaultClient) {
        Disposer.dispose(myCefClient);
      }
    });
  }

  @Override
  public boolean isDisposed() {
    return myDisposeHelper.isDisposed();
  }

  boolean isCefBrowserCreated() {
    return myIsCefBrowserCreated;
  }

  int getJSQueryCounter() {
    return myJSQueryCounter.incrementAndGet();
  }

  /**
   * Returns {@code JBCefBrowser} instance associated with this {@code CefBrowser}.
   */
  public static JBCefBrowser getJBCefBrowser(@NotNull CefBrowser browser) {
    return (JBCefBrowser)((JComponent)browser.getUIComponent()).getClientProperty(JBCEFBROWSER_INSTANCE_PROP);
  }

  /**
   * For internal usage.
   */
  public static void addOnBrowserMoveResizeCallback(@NotNull Consumer<JBCefBrowser> callback) {
    ourOnBrowserMoveResizeCallbacks.add(callback);
  }

  /**
   * For internal usage.
   */
  public static void removeOnBrowserMoveResizeCallback(@NotNull Consumer<JBCefBrowser> callback) {
    ourOnBrowserMoveResizeCallbacks.remove(callback);
  }

  protected class DefaultCefContextMenuHandler extends CefContextMenuHandlerAdapter {
    protected static final int DEBUG_COMMAND_ID = MENU_ID_USER_LAST;
    private final boolean isInternal;

    public DefaultCefContextMenuHandler(boolean isInternal) {
      this.isInternal = isInternal;
    }

    @Override
    public void onBeforeContextMenu(CefBrowser browser, CefFrame frame, CefContextMenuParams params, CefMenuModel model) {
      if (isInternal) {
        model.addItem(DEBUG_COMMAND_ID, "Open DevTools");
      }
    }

    @Override
    public boolean onContextMenuCommand(CefBrowser browser, CefFrame frame, CefContextMenuParams params, int commandId, int eventFlags) {
      if (commandId == DEBUG_COMMAND_ID) {
        openDevtools();
        return true;
      }
      return false;
    }
  }

  private class MyFTP extends FocusTraversalPolicy {
    @Override
    public Component getComponentAfter(Container aContainer, Component aComponent) {
      return myCefBrowser.getUIComponent();
    }

    @Override
    public Component getComponentBefore(Container aContainer, Component aComponent) {
      return myCefBrowser.getUIComponent();
    }

    @Override
    public Component getFirstComponent(Container aContainer) {
      return myCefBrowser.getUIComponent();
    }

    @Override
    public Component getLastComponent(Container aContainer) {
      return myCefBrowser.getUIComponent();
    }

    @Override
    public Component getDefaultComponent(Container aContainer) {
      return myCefBrowser.getUIComponent();
    }
  }
}
