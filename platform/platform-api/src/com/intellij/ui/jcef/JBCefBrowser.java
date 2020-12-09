// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.project.LightEditActionFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.ui.JBColor;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.IconUtil;
import com.intellij.util.LazyInitializer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.ImageUtil;
import com.jetbrains.cef.JCefAppConfig;
import com.jetbrains.cef.JCefVersionDetails;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefContextMenuParams;
import org.cef.callback.CefMenuModel;
import org.cef.handler.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static com.intellij.ui.jcef.JBCefEventUtils.convertCefKeyEvent;
import static com.intellij.ui.jcef.JBCefEventUtils.isUpDownKeyEvent;
import static org.cef.callback.CefMenuModel.MenuId.MENU_ID_USER_LAST;

/**
 * A wrapper over {@link CefBrowser}.
 * <p>
 * Use {@link #getComponent()} as the browser's UI component.
 * Use {@link #loadURL(String)} or {@link #loadHTML(String)} for loading.
 *
 * If you need to render output using your own handler, see {@link JBCefOsrHandlerBrowser}
 * @see JBCefOsrHandlerBrowser
 *
 * @author tav
 */
public class JBCefBrowser extends JBCefBrowserBase {

  @NotNull private final JPanel myComponent;
  @NotNull private final CefFocusHandler myCefFocusHandler;
  @NotNull private final CefKeyboardHandler myKeyboardHandler;
  @NotNull private static final List<Consumer<? super JBCefBrowser>> ourOnBrowserMoveResizeCallbacks =
    Collections.synchronizedList(new ArrayList<>(1));
  private final boolean myIsDefaultClient;
  private JDialog myDevtoolsFrame = null;
  protected CefContextMenuHandler myDefaultContextMenuHandler;
  @Nullable private final CefLoadHandler myLoadHandler;
  private static final LazyInitializer.NotNullValue<String> LOAD_ERROR_PAGE = new LazyInitializer.NotNullValue<>() {
    @Override
    public @NotNull String initialize() {
      try {
        URL url = JBCefApp.class.getResource("resources/load_error.html");
        if (url != null) return Files.readString(Paths.get(url.toURI()));
      }
      catch (IOException | URISyntaxException ex) {
        Logger.getInstance(JBCefBrowser.class).error("couldn't find load_error.html", ex);
      }
      return "";
    }
  };

  private static final LazyInitializer.NotNullValue<ScaleContext.Cache<String>> BASE64_ERR_IMAGE = new LazyInitializer.NotNullValue<>() {
    @Override
    public @NotNull ScaleContext.Cache<String> initialize() {
      return new ScaleContext.Cache<>((ctx) -> {
        BufferedImage image = ImageUtil.toBufferedImage(IconUtil.toImage(AllIcons.General.Error, ctx));
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
          ImageIO.write(image, "png", out);
          return Base64.getEncoder().encodeToString(out.toByteArray());
        }
        catch (IOException ex) {
          Logger.getInstance(JBCefBrowser.class).error("couldn't write an error image", ex);
        }
        return "";
      });
    }
  };

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

    private static Pair<String, AnAction> createAction(String shortcut, Consumer<CefFrame> action) {
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
    super(client, createBrowser(cefBrowser, client.getCefClient(), url), cefBrowser == null);
    if (client.isDisposed()) {
      throw new IllegalArgumentException("JBCefClient is disposed");
    }
    myIsDefaultClient = isDefaultClient;

    myComponent = createComponent();


    if (cefBrowser == null) {
      myCefClient.addLoadHandler(myLoadHandler = new CefLoadHandlerAdapter() {
        @Override
        public void onLoadError(CefBrowser browser, CefFrame frame, ErrorCode errorCode, String errorText, String failedUrl) {
          int fontSize = (int)(EditorColorsManager.getInstance().getGlobalScheme().getEditorFontSize() * 1.33);
          int bigFontSize = (int)(fontSize * 1.5);
          int bigFontSize_div_2 = bigFontSize / 2;
          int bigFontSize_div_3 = bigFontSize / 3;
          Color bgColor = JBColor.background();
          String bgWebColor = String.format("#%02x%02x%02x", bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue());
          Color fgColor = JBColor.foreground();
          String fgWebColor = String.format("#%02x%02x%02x", fgColor.getRed(), fgColor.getGreen(), fgColor.getBlue());
          String fgWebColor_05 = fgWebColor + "50";

          String html = LOAD_ERROR_PAGE.get();
          html = html.replace("${fontSize}", String.valueOf(fontSize));
          html = html.replace("${bigFontSize}", String.valueOf(bigFontSize));
          html = html.replace("${bigFontSize_div_2}", String.valueOf(bigFontSize_div_2));
          html = html.replace("${bigFontSize_div_3}", String.valueOf(bigFontSize_div_3));
          html = html.replace("${bgWebColor}", bgWebColor);
          html = html.replace("${fgWebColor}", fgWebColor);
          html = html.replace("${fgWebColor_05}", fgWebColor_05);
          html = html.replace("${errorText}", errorText);
          html = html.replace("${failedUrl}", failedUrl);
          html = html.replace("${base64Image}",
                              ObjectUtils.notNull(BASE64_ERR_IMAGE.get().getOrProvide(ScaleContext.create(getComponent())), ""));

          loadHTML(html);
        }
      }, myCefBrowser);
    } else {
      myLoadHandler = null;
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


    myCefClient.addKeyboardHandler(myKeyboardHandler = new CefKeyboardHandlerAdapter() {
      @Override
      public boolean onKeyEvent(CefBrowser browser, CefKeyEvent cefKeyEvent) {
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        boolean consume = focusOwner != browser.getUIComponent();
        if (consume && SystemInfoRt.isMac && isUpDownKeyEvent(cefKeyEvent)) return true; // consume

        Window focusedWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
        if (focusedWindow == null) {
          return true; // consume
        }
        KeyEvent javaKeyEvent = convertCefKeyEvent(cefKeyEvent, focusedWindow);
        Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(javaKeyEvent);
        return consume;
      }
    }, myCefBrowser);

    myDefaultContextMenuHandler = createDefaultContextMenuHandler();
    myCefClient.addContextMenuHandler(myDefaultContextMenuHandler, this.getCefBrowser());
  }


  @SuppressWarnings("deprecation") // New API is not available in JBR yet
  @NotNull
  private static CefBrowser createBrowser(@Nullable CefBrowser cefBrowser, @NotNull CefClient client, @Nullable String url) {
   // Uncomment when new JBR would arrive
   // CefRendering mode = JBCefApp.isOffScreenRenderingMode() ? CefRendering.OFFSCREEN : CefRendering.DEFAULT;
    boolean mode = JBCefApp.isOffScreenRenderingMode();
    return cefBrowser != null
           ?
           cefBrowser
           : client.createBrowser(url != null ? url : BLANK_URI, mode, false, null);
  }

  protected DefaultCefContextMenuHandler createDefaultContextMenuHandler() {
    boolean isInternal = ApplicationManager.getApplication().isInternal();
    return new DefaultCefContextMenuHandler(isInternal);
  }

  @NotNull
  private JPanel createComponent() {
    Component uiComp = getCefBrowser().getUIComponent();
    JPanel resultPanel = SystemInfoRt.isWindows ?
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
    resultPanel.setBackground(JBColor.background());
    resultPanel.putClientProperty(JBCEFBROWSER_INSTANCE_PROP, this);
    if (SystemInfoRt.isMac) {
      // We handle shortcuts manually on MacOS: https://www.magpcss.org/ceforum/viewtopic.php?f=6&t=12561
      ShortcutProvider.registerShortcuts(resultPanel, this);
    }
    resultPanel.add(uiComp, BorderLayout.CENTER);
    if (SystemInfoRt.isWindows) {
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

  @NotNull
  public JComponent getComponent() {
    return myComponent;
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
    super.dispose();
    myDisposeHelper.dispose(() -> {
      myCefClient.removeFocusHandler(myCefFocusHandler, myCefBrowser);
      myCefClient.removeKeyboardHandler(myKeyboardHandler, myCefBrowser);
      if (myLifeSpanHandler != null) myCefClient.removeLifeSpanHandler(myLifeSpanHandler, myCefBrowser);
      if (myLoadHandler != null) myCefClient.removeLoadHandler(myLoadHandler, myCefBrowser);
      myCefBrowser.stopLoad();
      myCefBrowser.close(true);
      if (myIsDefaultClient) {
        Disposer.dispose(myCefClient);
      }
    });
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
