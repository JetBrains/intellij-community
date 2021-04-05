// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.LightEditActionFactory;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.JBColor;
import com.intellij.util.ObjectUtils;
import com.jetbrains.cef.JCefAppConfig;
import com.jetbrains.cef.JCefVersionDetails;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefBrowserOsrWithHandler;
import org.cef.browser.CefFrame;
import org.cef.browser.CefRendering;
import org.cef.handler.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static com.intellij.ui.jcef.JBCefEventUtils.convertCefKeyEvent;
import static com.intellij.ui.jcef.JBCefEventUtils.isUpDownKeyEvent;

/**
 * A wrapper over {@link CefBrowser}.
 * <p>
 * Use {@link #getComponent()} as the browser's UI component.
 * <p>
 * Use {@link #loadURL(String)} or {@link #loadHTML(String)} for loading.
 * <p>
 * Use {@link JBCefOsrHandlerBrowser} to render offscreen via a custom {@link CefRenderHandler}.
 *
 * @see JBCefOsrHandlerBrowser
 * @author tav
 */
@SuppressWarnings("unused")
public class JBCefBrowser extends JBCefBrowserBase {
  /**
   * @see #setProperty(String, Object)
   */
  public static class Properties extends JBCefBrowserBase.Properties {
    /**
     * Defines whether the browser component should take focus on navigation (loading a new URL).
     * <p></p>
     * Accepts {@link Boolean} values. The default value is {@link Boolean#FALSE}.
     */
    public static final @NotNull String FOCUS_ON_NAVIGATION = "JBCefBrowser.focusOnNavigation";

    /**
     * Defines whether the browser component should take focus on show.
     * <p></p>
     * Accepts {@link Boolean} values. The default value is {@link Boolean#FALSE}.
     */
    public static final @NotNull String FOCUS_ON_SHOW ="JBCefBrowser.focusOnShow";

    static {
      PropertiesHelper.putType(FOCUS_ON_NAVIGATION, Boolean.class);
      PropertiesHelper.putType(FOCUS_ON_SHOW, Boolean.class);
    }
  }

  private static final @NotNull List<Consumer<? super JBCefBrowser>> ourOnBrowserMoveResizeCallbacks =
    Collections.synchronizedList(new ArrayList<>(1));

  static final @NotNull Dimension DEF_PREF_SIZE = new Dimension(800, 600);

  private static final class ShortcutProvider {
    // Since these CefFrame::* methods are available only with JCEF API 1.1 and higher, we are adding no shortcuts for older JCEF
    private static final List<Pair<String, AnAction>> ourActions = isSupportedByJCefApi() ? List.of(
      createAction("$Cut", CefFrame::cut),
      createAction("$Copy", CefFrame::copy),
      createAction("$Paste", CefFrame::paste),
      createAction("$Delete", CefFrame::delete),
      createAction("$SelectAll", CefFrame::selectAll),
      createAction("$Undo", CefFrame::undo),
      createAction("$Redo", CefFrame::redo)
    ) : List.of();

    // This method may be deleted when JCEF API version check is included into JBCefApp#isSupported
    private static boolean isSupportedByJCefApi() {
      try {
        /* getVersionDetails() was introduced alongside JCEF API versioning with first version of 1.1, which also added these necessary
         * for shortcuts to work CefFrame methods. Therefore successful call to getVersionDetails() means our JCEF API is at least 1.1 */
        JCefAppConfig.getVersionDetails();
        return true;
      }
      catch (NoSuchMethodError | JCefVersionDetails.VersionUnavailableException e) {
        Logger.getInstance(ShortcutProvider.class).warn("JCEF shortcuts are unavailable (incompatible API)", e);
        return false;
      }
    }

    private static Pair<String, AnAction> createAction(String shortcut, Consumer<? super CefFrame> action) {
      return Pair.create(
        shortcut,
        LightEditActionFactory.create(event -> {
          Component component = event.getData(PlatformDataKeys.CONTEXT_COMPONENT);
          if (component == null) return;
          Component parentComponent = component.getParent();
          if (!(parentComponent instanceof JComponent)) return;
          Object browser = ((JComponent)parentComponent).getClientProperty(JBCEFBROWSER_INSTANCE_PROP);
          if (!(browser instanceof JBCefBrowser)) return;
          action.accept(((JBCefBrowser) browser).getCefBrowser().getFocusedFrame());
        })
      );
    }

    private static void registerShortcuts(JComponent uiComp, JBCefBrowser jbCefBrowser) {
      ActionManager actionManager = ActionManager.getInstance();
      for (Pair<String, AnAction> action : ourActions) {
        action.second.registerCustomShortcutSet(actionManager.getAction(action.first).getShortcutSet(), uiComp, jbCefBrowser);
      }
    }
  }

  private final @NotNull JPanel myComponent;
  private final @NotNull CefFocusHandler myCefFocusHandler;
  private final @NotNull CefKeyboardHandler myKeyboardHandler;

  private volatile boolean myFirstShow = true;

  /**
   * Defines the way the browser is rendered onto its surface.
   */
  @ApiStatus.Experimental
  public enum RenderingType {
    /**
     * The browser is rendered on a platform heavyweight window embedded into the host frame.
     */
    EMBEDDED_WINDOW,
    /**
     * The browser is rendered off-screen on a heavyweight canvas via OGL.
     */
    OGL_CANVAS,
    /**
     * The browser is rendered off-screen on a lightweight Swing component via a buffered image.
     */
    BUFFERED_IMAGE,

    // [tav] todo
    //VOLATILE_IMAGE
  }

  /**
   * Creates a browser with the specified rendering type.
   * <p></p>
   * When {@code createImmediately} is {@code false}, call {@link CefBrowser#createImmediately()} via {@link #getCefBrowser()}.
   *
   * @param type the rendering type
   * @param client the client, a default client is used when null is passed
   * @param url the URL, a blank url is loaded when null is passed
   * @param createImmediately whether the {@code CefBrowser} should be created immediately
   * @return the new browser instance
   */
  @ApiStatus.Experimental
  public static @NotNull JBCefBrowser create(@NotNull RenderingType type, @Nullable JBCefClient client, @Nullable String url, boolean createImmediately) {
    return new JBCefBrowser(type, client, url, createImmediately);
  }

  protected JBCefBrowser(@NotNull RenderingType type, @Nullable JBCefClient client, @Nullable String url, boolean createImmediately) {
    this(createBrowser(type, client, url));
    CefBrowser cefBrowser = getCefBrowser();
    if (type == RenderingType.BUFFERED_IMAGE) {
      CefBrowserOsrWithHandler cefOsrBrowser = (CefBrowserOsrWithHandler)cefBrowser;
      ((JBCefOsrComponent)cefOsrBrowser.getUIComponent()).setBrowser(this);
      setProperty(JBCefBrowserBase.Properties.IS_LIGHTWEIGHT, Boolean.TRUE);
    }
    if (createImmediately) cefBrowser.createImmediately();
  }

  /**
   * Creates a browser with the provided {@code JBCefClient} and initial URL. The client's lifecycle is the responsibility of the caller.
   */
  public JBCefBrowser(@NotNull JBCefClient client, @Nullable String url) {
    this(client, false, url);
  }

  public JBCefBrowser(@NotNull CefBrowser cefBrowser, @NotNull JBCefClient client) {
    this(cefBrowser, false, client, false);
  }

  private JBCefBrowser(@NotNull JBCefClient client, boolean isDefaultClient, @Nullable String url) {
    this(createBrowser(RenderingType.EMBEDDED_WINDOW, client, url));
  }

  private JBCefBrowser(@NotNull CreateBrowserArtefacts artefacts) {
    this(artefacts.cefBrowser, true, artefacts.jbClient, artefacts.isDefaultClient);
  }

  private JBCefBrowser(@NotNull CefBrowser cefBrowser,
                       boolean isNewBrowserCreated,
                       @NotNull JBCefClient client,
                       boolean isDefaultClient)
  {
    super(client, cefBrowser, isNewBrowserCreated, isDefaultClient);
    if (client.isDisposed()) {
      throw new IllegalArgumentException("JBCefClient is disposed");
    }

    myComponent = createComponent();

    myCefClient.addFocusHandler(myCefFocusHandler = new CefFocusHandlerAdapter() {
      @Override
      public boolean onSetFocus(CefBrowser browser, FocusSource source) {
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        boolean componentFocused = focusOwner == getComponent() || focusOwner == getCefBrowser().getUIComponent();
        boolean focusOnNavigation = (myFirstShow && isProperty(Properties.FOCUS_ON_SHOW) ||
                                     isProperty(Properties.FOCUS_ON_NAVIGATION)) ||
                                    componentFocused;
        myFirstShow = false;

        if (source == FocusSource.FOCUS_SOURCE_NAVIGATION && !focusOnNavigation) {
          if (SystemInfo.isWindows) {
            myCefBrowser.setFocus(false);
          }
          return true; // suppress focusing the browser on navigation events
        }
        if (!browser.getUIComponent().hasFocus()) {
          if (SystemInfo.isLinux) {
            browser.getUIComponent().requestFocus();
          }
          else {
            browser.getUIComponent().requestFocusInWindow();
          }
        }
        return false;
      }
    }, myCefBrowser);

    myCefClient.addKeyboardHandler(myKeyboardHandler = new CefKeyboardHandlerAdapter() {
      @Override
      public boolean onKeyEvent(CefBrowser browser, CefKeyEvent cefKeyEvent) {
        if (isProperty(JBCefBrowserBase.Properties.IS_LIGHTWEIGHT)) return false;

        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        boolean consume = focusOwner != browser.getUIComponent();
        if (consume && SystemInfo.isMac && isUpDownKeyEvent(cefKeyEvent)) return true; // consume

        Window focusedWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
        if (focusedWindow == null) {
          return true; // consume
        }
        KeyEvent javaKeyEvent = convertCefKeyEvent(cefKeyEvent, focusedWindow);
        Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(javaKeyEvent);
        return consume;
      }
    }, myCefBrowser);
  }

  private static class CreateBrowserArtefacts {
    final @NotNull CefBrowser cefBrowser;
    final @NotNull JBCefClient jbClient;
    boolean isDefaultClient;

    CreateBrowserArtefacts(@NotNull CefBrowser cefBrowser, @NotNull JBCefClient jbClient, boolean isDefaultClient) {
      this.cefBrowser = cefBrowser;
      this.jbClient = jbClient;
      this.isDefaultClient = isDefaultClient;
    }
  }

  private static @NotNull CreateBrowserArtefacts createBrowser(@NotNull RenderingType type, @Nullable JBCefClient client, @Nullable String url) {
    boolean isDefaultClient = client == null;
    if (isDefaultClient) client = JBCefApp.getInstance().createClient();
    switch (type) {
      case EMBEDDED_WINDOW:
        return new CreateBrowserArtefacts(
          client.getCefClient().createBrowser(ObjectUtils.notNull(url, BLANK_URI), CefRendering.DEFAULT, false, null),
          client,
          isDefaultClient);
      case OGL_CANVAS:
        JBCefApp.checkOffScreenRenderingModeEnabled();
        return new CreateBrowserArtefacts(
          client.getCefClient().createBrowser(ObjectUtils.notNull(url, BLANK_URI), CefRendering.OFFSCREEN, false, null),
          client,
          isDefaultClient);
      case BUFFERED_IMAGE:
        JBCefApp.checkOffScreenRenderingModeEnabled();
        JBCefOsrComponent comp = new JBCefOsrComponent();
        JBCefOsrHandler handler = new JBCefOsrHandler(comp);
        comp.setRenderHandler(handler);
        return new CreateBrowserArtefacts(
          new CefBrowserOsrWithHandler(client.getCefClient(), url, null, handler, comp),
          client,
          isDefaultClient);
    }
    throw new IllegalArgumentException("wrong RenderingType"); // unreachable
  }

  private @NotNull JPanel createComponent() {
    Component uiComp = getCefBrowser().getUIComponent();
    JPanel resultPanel = new JPanel(new BorderLayout()) {
      {
        enableEvents(AWTEvent.MOUSE_WHEEL_EVENT_MASK);
      }
      @Override
      public void setBackground(Color bg) {
        uiComp.setBackground(bg);
        super.setBackground(bg);
      }
      @Override
      public void removeNotify() {
        if (SystemInfo.isWindows) {
          if (myCefBrowser.getUIComponent().hasFocus()) {
            // pass focus before removal
            myCefBrowser.setFocus(false);
          }
        }
        myFirstShow = true;
        super.removeNotify();
      }
      @Override
      public Dimension getPreferredSize() {
        // Preferred size should not be zero, otherwise the content loading is not triggered
        Dimension size = super.getPreferredSize();
        return size.width > 0 && size.height > 0 ? size : DEF_PREF_SIZE;
      }
      @Override
      protected void processFocusEvent(FocusEvent e) {
        super.processFocusEvent(e);
        if (e.getID() == FocusEvent.FOCUS_GAINED) {
          uiComp.requestFocusInWindow();
        }
      }
    };

    resultPanel.setBackground(JBColor.background());
    resultPanel.putClientProperty(JBCEFBROWSER_INSTANCE_PROP, this);
    if (SystemInfo.isMac) {
      // We handle shortcuts manually on MacOS: https://www.magpcss.org/ceforum/viewtopic.php?f=6&t=12561
      ShortcutProvider.registerShortcuts(resultPanel, this);
    }
    resultPanel.add(uiComp, BorderLayout.CENTER);
    if (SystemInfo.isWindows) {
      myCefBrowser.getUIComponent().addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          if (myCefBrowser.getUIComponent().isFocusable()) {
            getCefBrowser().setFocus(true);
          }
        }
      });
    }

    resultPanel.setFocusCycleRoot(true);
    resultPanel.setFocusTraversalPolicyProvider(true);
    resultPanel.setFocusTraversalPolicy(new MyFTP());

    resultPanel.addComponentListener(new ComponentAdapter() {
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
    return resultPanel;
  }

  /**
   * For internal usage.
   */
  public static void addOnBrowserMoveResizeCallback(@NotNull Consumer<? super JBCefBrowser> callback) {
    ourOnBrowserMoveResizeCallbacks.add(callback);
  }

  /**
   * For internal usage.
   */
  public static void removeOnBrowserMoveResizeCallback(@NotNull Consumer<? super JBCefBrowser> callback) {
    ourOnBrowserMoveResizeCallbacks.remove(callback);
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

  @Override
  public @NotNull JComponent getComponent() {
    return myComponent;
  }

  /**
   * Supports {@link Properties}.
   *
   * @throws IllegalArgumentException if the value has wrong type or format
   */
  @Override
  public void setProperty(@NotNull String name, @Nullable Object value) {
    super.setProperty(name, value);
  }

  @Override
  public void dispose() {
    super.dispose(() -> {
      myCefClient.removeFocusHandler(myCefFocusHandler, myCefBrowser);
      myCefClient.removeKeyboardHandler(myKeyboardHandler, myCefBrowser);
    });
  }

  // for binary compatibility
  protected class DefaultCefContextMenuHandler extends JBCefBrowserBase.DefaultCefContextMenuHandler {
    public DefaultCefContextMenuHandler(boolean isInternal) {
      super(isInternal);
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
