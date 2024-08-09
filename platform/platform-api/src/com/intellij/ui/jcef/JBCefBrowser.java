// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.JBColor;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.handler.CefFocusHandler;
import org.cef.handler.CefFocusHandlerAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * A wrapper over {@link CefBrowser}.
 * <p>
 * Use {@link #getComponent()} as the browser's UI component.
 * <p>
 * Use {@link #loadURL(String)} or {@link #loadHTML(String)} for loading.
 *
 * @see #createBuilder
 * @see JBCefOsrHandlerBrowser
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/jcef.html">Embedded Browser (JCEF) (IntelliJ Platform Docs)</a>
 */
public class JBCefBrowser extends JBCefBrowserBase {
  protected JBCefBrowser(@NotNull JBCefBrowserBuilder builder) {
    super(builder);

    if (myCefClient.isDisposed()) {
      throw new IllegalArgumentException("JBCefClient is disposed");
    }

    myComponent = createComponent(builder.myMouseWheelEventEnable);

    myCefClient.addFocusHandler(myCefFocusHandler = new CefFocusHandlerAdapter() {
      @Override
      public void onTakeFocus(CefBrowser browser, boolean next) {
        super.onTakeFocus(browser, next);
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        focusedBrowser = null;
      }

      @Override
      public void onGotFocus(CefBrowser browser) {
        super.onGotFocus(browser);
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        focusedBrowser = new WeakReference<>(JBCefBrowser.this);
      }

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
            if (isOffScreenRendering()) {
              browser.getUIComponent().requestFocusInWindow();
            }
            else {
              browser.getUIComponent().requestFocus();
            }
          }
          else {
            browser.getUIComponent().requestFocusInWindow();
          }
        }
        return false;
      }
    }, myCefBrowser);
  }

  private static final @NotNull List<Consumer<? super JBCefBrowser>> ourOnBrowserMoveResizeCallbacks =
    Collections.synchronizedList(new ArrayList<>(1));

  static final @NotNull Dimension DEF_PREF_SIZE = new Dimension(800, 600);



  private final @NotNull JPanel myComponent;
  private final @NotNull CefFocusHandler myCefFocusHandler;


  private volatile boolean myFirstShow = true;

  /**
   * Creates a browser builder.
   */
  public static @NotNull JBCefBrowserBuilder createBuilder() {
    return new JBCefBrowserBuilder();
  }

  /**
   * Creates a browser according to the provided builder options.
   */
  public static @NotNull JBCefBrowser create(@NotNull JBCefBrowserBuilder builder) {
    return new JBCefBrowser(builder);
  }

  /**
   * Creates a browser with default {@link JBCefClient}.
   * The default client is disposed with this browser and must not be used with other browsers.
   *
   * @see #createBuilder
   */
  public JBCefBrowser() {
    this(createBuilder());
  }

  /**
   * Creates a browser with the initial URL.
   *
   * @see #createBuilder
   * @see JBCefBrowserBuilder#setUrl(String)
   */
  public JBCefBrowser(@NotNull String url) {
    this(createBuilder().setUrl(url));
  }

  /**
   * Creates a browser with the provided {@code JBCefClient} and initial URL. The client's lifecycle is the responsibility of the caller.
   *
   * @see #createBuilder
   * @see JBCefBrowserBuilder#setClient(JBCefClient)
   * @see JBCefBrowserBuilder#setUrl(String)
   * @deprecated use {@link JBCefBrowserBuilder} instead
   */
  @Deprecated
  public JBCefBrowser(@NotNull JBCefClient client, @Nullable String url) {
    this(createBuilder().setClient(client).setUrl(url));
  }

  /**
   * Creates a browser wrapping the provided {@link CefBrowser} with the provided {@link JBCefClient}.
   *
   * @see #createBuilder
   * @see JBCefBrowserBuilder#setCefBrowser(CefBrowser)
   * @see JBCefBrowserBuilder#setClient(JBCefClient)
   * @deprecated use {@link JBCefBrowserBuilder} instead
   */
  @Deprecated
  public JBCefBrowser(@NotNull CefBrowser cefBrowser, @NotNull JBCefClient client) {
    this(createBuilder().setCefBrowser(cefBrowser).setClient(client));
  }

  private @NotNull JPanel createComponent(boolean isMouseWheelEventEnabled) {
    Component uiComp = getCefBrowser().getUIComponent();
    JPanel resultPanel = new MyPanel(uiComp, isMouseWheelEventEnabled);

    resultPanel.setBackground(getBackgroundColor());
    resultPanel.putClientProperty(JBCEFBROWSER_INSTANCE_PROP, this);
    if (SystemInfo.isMac) {
      // We handle shortcuts manually on MacOS: https://www.magpcss.org/ceforum/viewtopic.php?f=6&t=12561
      JcefShortcutProvider.registerShortcuts(resultPanel, this);
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
   * @see #setProperty(String, Object)
   */
  public static class Properties extends JBCefBrowserBase.Properties {
    /**
     * Defines whether the browser component should take focus on navigation (loading a new URL).
     * <p>
     * Accepts {@link Boolean} values. The default value is {@link Boolean#FALSE}.
     */
    public static final @NotNull String FOCUS_ON_NAVIGATION = "JBCefBrowser.focusOnNavigation";

    /**
     * Defines whether the browser component should take focus on show.
     * <p>
     * Accepts {@link Boolean} values. The default value is {@link Boolean#FALSE}.
     */
    public static final @NotNull String FOCUS_ON_SHOW = "JBCefBrowser.focusOnShow";

    static {
      PropertiesHelper.setType(FOCUS_ON_NAVIGATION, Boolean.class);
      PropertiesHelper.setType(FOCUS_ON_SHOW, Boolean.class);
    }
  }

  protected @NotNull Color getBackgroundColor() {
    return JBColor.background();
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

  @Override
  public @NotNull JComponent getComponent() {
    return myComponent;
  }

  @Override
  public void dispose() {
    super.dispose(() -> {
      myCefClient.removeFocusHandler(myCefFocusHandler, myCefBrowser);
    });
  }

  // for binary compatibility
  protected class DefaultCefContextMenuHandler extends JBCefBrowserBase.DefaultCefContextMenuHandler {
    public DefaultCefContextMenuHandler() {
      super();
    }

    public DefaultCefContextMenuHandler(boolean isOpenDevToolsItemEnabled) {
      super(isOpenDevToolsItemEnabled);
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

  public class MyPanel extends JPanel {
    private final Component myUiComp;

    private MyPanel(Component uiComp, boolean isMouseWheelEventEnabled) {
      super(new BorderLayout());
      myUiComp = uiComp;
      myUiComp.setBackground(getBackground());
      if (isMouseWheelEventEnabled) {
        enableEvents(AWTEvent.MOUSE_WHEEL_EVENT_MASK);
      }

      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        CefSettings settings = JBCefApp.getInstance().getCefSettings();
        if (settings != null && !settings.no_sandbox && JBCefAppArmorUtils.areUnprivilegedUserNamespacesRestricted()) {
          SwingUtilities.invokeLater(() -> {
            removeAll();
            add(JBCefAppArmorUtils.getUnprivilegedUserNamespacesRestrictedStubPanel(), BorderLayout.CENTER);
            revalidate();
            repaint();
          });
        }
      });
    }

    @Override
    public void setBackground(Color bg) {
      if (myUiComp != null) myUiComp.setBackground(bg);
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
        myUiComp.requestFocusInWindow();
      }
    }

    public @NotNull JBCefBrowser getJBCefBrowser() {
      return JBCefBrowser.this;
    }
  }
}