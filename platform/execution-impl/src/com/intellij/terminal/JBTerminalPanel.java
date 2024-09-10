// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal;

import com.intellij.application.options.EditorFontsConstants;
import com.intellij.ide.DataManager;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.ComplementaryFontsRegistry;
import com.intellij.openapi.editor.impl.FontInfo;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.terminal.actions.TerminalActionWrapper;
import com.intellij.util.JBHiDPIScaledImage;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.jediterm.terminal.ProcessTtyConnector;
import com.jediterm.terminal.TerminalCopyPasteHandler;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.TerminalAction;
import com.jediterm.terminal.ui.TerminalActionMenuBuilder;
import com.jediterm.terminal.ui.TerminalActionProvider;
import com.jediterm.terminal.ui.TerminalPanel;
import com.pty4j.windows.conpty.WinConPtyProcess;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class JBTerminalPanel extends TerminalPanel implements FocusListener, Disposable {
  private static final Logger LOG = Logger.getInstance(JBTerminalPanel.class);
  private static final @NonNls String[] ACTIONS_TO_SKIP = new String[]{
    "ActivateTerminalToolWindow",
    "ActivateProjectToolWindow",
    "ActivateFavoritesToolWindow",
    "ActivateBookmarksToolWindow",
    "ActivateFindToolWindow",
    "ActivateRunToolWindow",
    "ActivateDebugToolWindow",
    "ActivateProblemsViewToolWindow",
    "ActivateTODOToolWindow",
    "ActivateStructureToolWindow",
    "ActivateHierarchyToolWindow",
    "ActivateServicesToolWindow",
    "ActivateCommitToolWindow",
    "ActivateVersionControlToolWindow",
    "HideActiveWindow",
    "HideAllWindows",

    "NextWindow",
    "PreviousWindow",
    "NextProjectWindow",
    "PreviousProjectWindow",

    "ShowBookmarks",
    "ShowTypeBookmarks",
    "FindInPath",
    "GotoBookmark0",
    "GotoBookmark1",
    "GotoBookmark2",
    "GotoBookmark3",
    "GotoBookmark4",
    "GotoBookmark5",
    "GotoBookmark6",
    "GotoBookmark7",
    "GotoBookmark8",
    "GotoBookmark9",

    "GotoAction",
    "GotoFile",
    "GotoClass",
    "GotoSymbol",

    "Vcs.Push",

    "ShowSettings",
    "RecentFiles",
    "Switcher",

    "ResizeToolWindowLeft",
    "ResizeToolWindowRight",
    "ResizeToolWindowUp",
    "ResizeToolWindowDown",
    "MaximizeToolWindow",
    
    "MaintenanceAction",

    "TerminalIncreaseFontSize",
    "TerminalDecreaseFontSize",
    "TerminalResetFontSize"
  };

  private final TerminalEventDispatcher myEventDispatcher = new TerminalEventDispatcher();
  private final JBTerminalSystemSettingsProviderBase mySettingsProvider;
  private final TerminalEscapeKeyListener myEscapeKeyListener;
  private final List<Consumer<? super KeyEvent>> myPreKeyEventConsumers = new CopyOnWriteArrayList<>();

  private List<AnAction> myActionsToSkip;

  public JBTerminalPanel(@NotNull JBTerminalSystemSettingsProviderBase settingsProvider,
                         @NotNull TerminalTextBuffer backBuffer,
                         @NotNull StyleState styleState) {
    super(settingsProvider, backBuffer, styleState);

    mySettingsProvider = settingsProvider;

    addFocusListener(this);

    mySettingsProvider.getUiSettingsManager().addListener(this);
    setDefaultCursorShape(settingsProvider.getCursorShape());
    myEscapeKeyListener = new TerminalEscapeKeyListener(this);
  }

  @Override
  public Dimension getMinimumSize() {
    if (isMinimumSizeSet()) {
      return super.getMinimumSize();
    }
    return JBUI.emptySize();
  }

  private boolean skipKeyEvent(@NotNull KeyEvent e) {
    return skipAction(e, myActionsToSkip);
  }

  private static boolean skipAction(@NotNull KeyEvent e, @Nullable List<? extends AnAction> actionsToSkip) {
    if (actionsToSkip != null) {
      final KeyboardShortcut eventShortcut = new KeyboardShortcut(KeyStroke.getKeyStrokeForEvent(e), null);
      for (AnAction action : actionsToSkip) {
        for (Shortcut sc : action.getShortcutSet().getShortcuts()) {
          if (sc.isKeyboard() && sc.startsWith(eventShortcut)) {
            if (!Registry.is("terminal.Ctrl-E.opens.RecentFiles.popup", false) &&
                IdeActions.ACTION_RECENT_FILES.equals(ActionManager.getInstance().getId(action))) {
              if (e.getModifiersEx() == InputEvent.CTRL_DOWN_MASK && e.getKeyCode() == KeyEvent.VK_E) {
                return false;
              }
            }
            return true;
          }
        }
      }
    }
    return false;
  }

  @Override
  public void handleKeyEvent(@NotNull KeyEvent e) {
    for (Consumer<? super KeyEvent> preKeyEventConsumer : myPreKeyEventConsumers) {
      preKeyEventConsumer.accept(e);
    }
    myEscapeKeyListener.handleKeyEvent(e);
    if (!e.isConsumed()) {
      super.handleKeyEvent(e);
    }
  }

  public void addPreKeyEventHandler(@NotNull Consumer<? super KeyEvent> preKeyEventHandler) {
    myPreKeyEventConsumers.add(preKeyEventHandler);
  }

  @Override
  public List<TerminalAction> getActions() {
    List<TerminalAction> actions = super.getActions();
    String clearBufferActionName = mySettingsProvider.getClearBufferActionPresentation().getName();
    TerminalAction clearBufferAction = ContainerUtil.find(actions, action -> action.getName().equals(clearBufferActionName));
    if (clearBufferAction != null) {
      clearBufferAction.withEnabledSupplier(() -> {
        if (getTerminalTextBuffer().isUsingAlternateBuffer()) {
          return false;
        }
        JBTerminalWidget terminalWidget = DataManager.getInstance().getDataContext(this).getData(JBTerminalWidget.TERMINAL_DATA_KEY);
        if (terminalWidget == null || terminalWidget.getTerminalPanel() != this) {
          return false;
        }
        ProcessTtyConnector connector = terminalWidget.getProcessTtyConnector();
        WinConPtyProcess winConPtyProcess = connector != null ? ObjectUtils.tryCast(connector.getProcess(), WinConPtyProcess.class) : null;
        return winConPtyProcess == null;
      });
    }
    return actions;
  }

  @Override
  protected void setupAntialiasing(Graphics graphics) {
    UIUtil.setupComposite((Graphics2D)graphics);
    UISettings.setupAntialiasing(graphics);
  }

  @Override
  protected @NotNull TerminalCopyPasteHandler createCopyPasteHandler() {
    return new IdeTerminalCopyPasteHandler();
  }

  @Override
  protected @NotNull JPopupMenu createPopupMenu(@NotNull TerminalActionProvider actionProvider) {
    return ActionManager.getInstance().createActionPopupMenu(
      ActionPlaces.TOOLWINDOW_POPUP,
      wrapTerminalActions(actionProvider)
    ).getComponent();
  }

  private static @NotNull ActionGroup wrapTerminalActions(@NotNull TerminalActionProvider provider) {
    var result = new DefaultActionGroup();
    TerminalAction.buildMenu(provider, new TerminalActionMenuBuilder() {
      @Override
      public void addAction(@NotNull TerminalAction action) {
        result.add(new TerminalActionWrapper(action));
      }

      @Override
      public void addSeparator() {
        result.addSeparator();
      }
    });
    return result;
  }

  @Override
  protected void drawImage(Graphics2D gfx, BufferedImage image, int x, int y, ImageObserver observer) {
    UIUtil.drawImage(gfx, image, x, y, observer);
  }

  @Override
  protected void drawImage(Graphics2D g, BufferedImage image, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2) {
    drawImage(g, image, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, null);
  }

  public static void drawImage(Graphics g,
                               Image image,
                               int dx1,
                               int dy1,
                               int dx2,
                               int dy2,
                               int sx1,
                               int sy1,
                               int sx2,
                               int sy2,
                               ImageObserver observer) {
    if (image instanceof JBHiDPIScaledImage) {
      final Graphics2D newG = (Graphics2D)g.create(0, 0, image.getWidth(observer), image.getHeight(observer));
      newG.scale(0.5, 0.5);
      Image img = ((JBHiDPIScaledImage)image).getDelegate();
      if (img == null) {
        img = image;
      }
      newG.drawImage(img, 2 * dx1, 2 * dy1, 2 * dx2, 2 * dy2, sx1 * 2, sy1 * 2, sx2 * 2, sy2 * 2, observer);
      newG.scale(1, 1);
      newG.dispose();
    }
    else {
      g.drawImage(image, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, observer);
    }
  }

  @Override
  protected BufferedImage createBufferedImage(int width, int height) {
    return ImageUtil.createImage(width, height, BufferedImage.TYPE_INT_ARGB);
  }


  @Override
  public void focusGained(FocusEvent event) {
    if (mySettingsProvider.overrideIdeShortcuts()) {
      myActionsToSkip = setupActionsToSkip();
      myEventDispatcher.register();
    }
    else {
      myActionsToSkip = null;
      myEventDispatcher.unregister();
    }

    if (GeneralSettings.getInstance().isSaveOnFrameDeactivation()) {
      ApplicationManager.getApplication().invokeLater(() -> FileDocumentManager.getInstance().saveAllDocuments(), ModalityState.nonModal());
    }
  }

  private static @NotNull List<AnAction> setupActionsToSkip() {
    List<AnAction> res = new ArrayList<>();
    ActionManager actionManager = ActionManager.getInstance();
    for (String actionId : ACTIONS_TO_SKIP) {
      AnAction action = actionManager.getAction(actionId);
      if (action != null) {
        res.add(action);
      }
    }
    return res;
  }

  @Override
  public void focusLost(FocusEvent event) {
    myActionsToSkip = null;
    myEventDispatcher.unregister();
    SaveAndSyncHandler.getInstance().scheduleRefresh();
  }

  @Override
  protected @NotNull Font getFontToDisplay(char[] text, int start, int end, @NotNull TextStyle style) {
    int fontStyle = Font.PLAIN;
    if (style.hasOption(TextStyle.Option.BOLD)) {
      fontStyle |= Font.BOLD;
    }
    if (style.hasOption(TextStyle.Option.ITALIC)) {
      fontStyle |= Font.ITALIC;
    }
    FontInfo fontInfo = ComplementaryFontsRegistry.getFontAbleToDisplay(
      text, start, end, fontStyle,
      mySettingsProvider.getColorsScheme().getConsoleFontPreferences(),
      null);
    return fontInfo.getFont().deriveFont((float)mySettingsProvider.getUiSettingsManager().getFontSize());
  }

  public void fontChanged() {
    reinitFontAndResize();
  }

  @Override
  protected void processMouseWheelEvent(MouseWheelEvent e) {
    if (EditorSettingsExternalizable.getInstance().isWheelFontChangeEnabled() && EditorUtil.isChangeFontSize(e)) {
      int newFontSize = (int)mySettingsProvider.getTerminalFontSize() - e.getWheelRotation();
      if (newFontSize >= EditorFontsConstants.getMinEditorFontSize() && newFontSize <= EditorFontsConstants.getMaxEditorFontSize()) {
        mySettingsProvider.getUiSettingsManager().setFontSize(newFontSize);
      }
      return;
    }
    super.processMouseWheelEvent(e);
  }

  @NotNull JBTerminalSystemSettingsProviderBase getSettingsProvider() {
    return mySettingsProvider;
  }

  @Nullable ToolWindow getContextToolWindow() {
    return DataManager.getInstance().getDataContext(this).getData(PlatformDataKeys.TOOL_WINDOW);
  }

  @Nullable Project getContextProject() {
    return DataManager.getInstance().getDataContext(this).getData(CommonDataKeys.PROJECT);
  }

  /**
   * Adds "Override IDE shortcuts" terminal feature allowing terminal to process all the key events.
   * Without own IdeEventQueue.EventDispatcher, terminal won't receive key events corresponding to IDE action shortcuts.
   */
  private final class TerminalEventDispatcher implements IdeEventQueue.EventDispatcher {

    private boolean myRegistered = false;

    @Override
    public boolean dispatch(@NotNull AWTEvent e) {
      if (e instanceof KeyEvent) {
        dispatchKeyEvent((KeyEvent)e);
      }
      return false;
    }

    private void dispatchKeyEvent(@NotNull KeyEvent e) {
      if (e.getID() == KeyEvent.KEY_PRESSED && !skipKeyEvent(e)) {
        if (!JBTerminalPanel.this.isFocusOwner()) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Prevented attempt to process " + KeyStroke.getKeyStrokeForEvent(e) + " by not focused " +
                      getDebugTerminalPanelName() + ", unregistering");
          }
          unregister();
          return;
        }
        if (LOG.isDebugEnabled()) {
          LOG.debug("Consuming " + KeyStroke.getKeyStrokeForEvent(e) + ", registered:" + myRegistered);
        }
        JBTerminalPanel.this.dispatchEvent(e);
      }
    }

    void register() {
      ThreadingAssertions.assertEventDispatchThread();
      if (LOG.isDebugEnabled()) {
        LOG.debug("Register terminal event dispatcher for " + getDebugTerminalPanelName());
      }
      if (myRegistered) {
        LOG.info("Already registered terminal event dispatcher");
      }
      else {
        if (Disposer.isDisposed(JBTerminalPanel.this)) {
          LOG.info("Already disposed " + JBTerminalPanel.this);
        }
        else {
          IdeEventQueue.getInstance().addDispatcher(this, JBTerminalPanel.this);
          myRegistered = true;
        }
      }
    }

    void unregister() {
      ThreadingAssertions.assertEventDispatchThread();
      if (LOG.isDebugEnabled()) {
        LOG.debug("Unregister terminal event dispatcher for " + getDebugTerminalPanelName());
      }
      if (myRegistered) {
        IdeEventQueue.getInstance().removeDispatcher(this);
      }
      myRegistered = false;
    }

    private @NotNull String getDebugTerminalPanelName() {
      return JBTerminalPanel.class.getSimpleName() + "@" + System.identityHashCode(JBTerminalPanel.this);
    }
  }
}
