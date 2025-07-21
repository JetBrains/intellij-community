// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal;

import com.intellij.execution.filters.Filter;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiCompatibleDataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.terminal.search.DefaultJediTermSearchComponent;
import com.intellij.terminal.search.JediTermSearchComponentProvider;
import com.intellij.terminal.session.TerminalSession;
import com.intellij.terminal.ui.TerminalWidget;
import com.intellij.terminal.ui.TtyConnectorAccessor;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.LineSeparator;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBSwingUtilities;
import com.intellij.util.ui.RegionPainter;
import com.jediterm.core.compatibility.Point;
import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.*;
import com.jediterm.terminal.model.SelectionUtil;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalSelection;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.*;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import kotlin.Pair;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class JBTerminalWidget extends JediTermWidget implements Disposable, UiCompatibleDataProvider {
  private static final Logger LOG = Logger.getInstance(JBTerminalWidget.class);

  public static final DataKey<JBTerminalWidget> TERMINAL_DATA_KEY = DataKey.create(JBTerminalWidget.class.getName());
  public static final DataKey<String> SELECTED_TEXT_DATA_KEY = DataKey.create(JBTerminalWidget.class.getName() + " selected text");

  private JBTerminalWidgetListener myListener;
  private final Project myProject;
  private final @NotNull TerminalTitle myTerminalTitle = new TerminalTitle();
  private final @NotNull JediTermHyperlinkFilterAdapter myHyperlinkFilter;

  public JBTerminalWidget(@NotNull Project project,
                          @NotNull JBTerminalSystemSettingsProviderBase settingsProvider,
                          @NotNull Disposable parent) {
    this(project, 80, 24, settingsProvider, null, parent);
  }

  public JBTerminalWidget(@NotNull Project project,
                          int columns,
                          int lines,
                          @NotNull JBTerminalSystemSettingsProviderBase settingsProvider,
                          @Nullable TerminalExecutionConsole console,
                          @NotNull Disposable parent) {
    super(columns, lines, settingsProvider);
    myProject = project;
    myHyperlinkFilter = new JediTermHyperlinkFilterAdapter(project, console, this);
    addAsyncHyperlinkFilter(myHyperlinkFilter);
    Disposer.register(parent, this);
    Disposer.register(this, myBridge);
    setFocusTraversalPolicy(new DefaultFocusTraversalPolicy() {
      @Override
      public Component getDefaultComponent(Container aContainer) {
        return getTerminalPanel();
      }
    });
    TerminalTitleKt.bindApplicationTitle(myTerminalTitle, getTerminal(), this);
  }

  public JBTerminalWidgetListener getListener() {
    return myListener;
  }

  public void setListener(JBTerminalWidgetListener listener) {
    myListener = listener;
  }

  public @NotNull Project getProject() {
    return myProject;
  }

  @Override
  protected @NotNull TerminalExecutorServiceManager createExecutorServiceManager() {
    return new TerminalExecutorServiceManagerImpl();
  }

  @Override
  protected JBTerminalPanel createTerminalPanel(@NotNull SettingsProvider settingsProvider,
                                                @NotNull StyleState styleState,
                                                @NotNull TerminalTextBuffer textBuffer) {
    JBTerminalPanel panel = new JBTerminalPanel((JBTerminalSystemSettingsProviderBase)settingsProvider, textBuffer, styleState);

    Disposer.register(this, panel);
    return panel;
  }

  @Override
  public @NotNull JBTerminalPanel getTerminalPanel() {
    return (JBTerminalPanel)super.getTerminalPanel();
  }

  @Override
  protected Graphics getComponentGraphics(Graphics graphics) {
    return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics));
  }

  @Override
  protected JScrollBar createScrollBar() {
    JBScrollBar bar = new JBScrollBar() {
      @Override
      public Color getBackground() {
        return myTerminalPanel.getBackground();
      }
    };
    bar.setOpaque(true);
    bar.putClientProperty(JBScrollPane.Alignment.class, JBScrollPane.Alignment.RIGHT);
    bar.putClientProperty(JBScrollBar.TRACK, (RegionPainter<Object>)(g, x, y, width, height, object) -> {
      SubstringFinder.FindResult result = myTerminalPanel.getFindResult();
      if (result != null) {
        int modelHeight = bar.getModel().getMaximum() - bar.getModel().getMinimum();

        TerminalColor backgroundColor = mySettingsProvider.getFoundPatternColor().getBackground();
        if (backgroundColor != null) {
          g.setColor(AwtTransformers.toAwtColor(mySettingsProvider.getTerminalColorPalette().getBackground(backgroundColor)));
        }
        int anchorHeight = Math.max(2, height / modelHeight);
        for (SubstringFinder.FindResult.FindItem r : result.getItems()) {
          int where = height * r.getStart().y / modelHeight;
          g.fillRect(x, y + where, width, anchorHeight);
        }
      }
    });
    return bar;
  }

  /**
   * @deprecated use {@link JBTerminalSystemSettingsProviderBase#getTerminalFontSize()} instead
   */
  @Deprecated
  public int getFontSize() {
    return Math.round(getSettingsProvider().getTerminalFontSize());
  }

  /**
   * @deprecated use {@link JBTerminalSystemSettingsProviderBase#getTerminalFontSize()} instead
   */
  @Deprecated
  public float getFontSize2D() {
    return getSettingsProvider().getTerminalFontSize();
  }

  /**
   * @deprecated use {@link JBTerminalSystemSettingsProviderBase#setTerminalFontSize(float)} instead
   */
  @Deprecated
  public void setFontSize(int fontSize) {
    getSettingsProvider().setTerminalFontSize(fontSize);
  }

  /**
   * @deprecated use {@link JBTerminalSystemSettingsProviderBase#setTerminalFontSize(float)} instead
   */
  @Deprecated
  public void setFontSize(float fontSize) {
    getSettingsProvider().setTerminalFontSize(fontSize);
  }

  /**
   * @deprecated use {@link JBTerminalSystemSettingsProviderBase#resetTerminalFontSize()} instead
   */
  @Deprecated
  public void resetFontSize() {
    getSettingsProvider().resetTerminalFontSize();
  }

  public @Nullable ProcessTtyConnector getProcessTtyConnector() {
    return ObjectUtils.tryCast(getTtyConnector(), ProcessTtyConnector.class);
  }

  static boolean isTerminalToolWindow(@Nullable ToolWindow toolWindow) {
    return toolWindow != null && "Terminal".equals(toolWindow.getId()); // TerminalToolWindowFactory.TOOL_WINDOW_ID is not visible here
  }

  @Override
  public void dispose() {
    close();
  }

  @Override
  protected @NotNull JediTermSearchComponent createSearchComponent() {
    JediTermSearchComponentProvider provider = ApplicationManager.getApplication().getService(JediTermSearchComponentProvider.class);
    return provider != null ? provider.createSearchComponent(this) : new DefaultJediTermSearchComponent();
  }

  public void addMessageFilter(@NotNull Filter filter) {
    myHyperlinkFilter.addFilter(filter);
  }

  public void start(TtyConnector connector) {
    setTtyConnector(connector);
    start();
  }

  public @NotNull JBTerminalSystemSettingsProviderBase getSettingsProvider() {
    return (JBTerminalSystemSettingsProviderBase)mySettingsProvider;
  }

  public void notifyStarted() {
    if (myListener != null) {
      myListener.onTerminalStarted();
    }
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(SELECTED_TEXT_DATA_KEY, getSelectedText());
    sink.set(TERMINAL_DATA_KEY, this);
  }

  static @Nullable String getSelectedText(@NotNull TerminalPanel terminalPanel) {
    TerminalSelection selection = terminalPanel.getSelection();
    if (selection == null) return null;
    TerminalTextBuffer buffer = terminalPanel.getTerminalTextBuffer();
    buffer.lock();
    try {
      Pair<Point, Point> points = selection.pointsForRun(buffer.getWidth());
      return SelectionUtil.getSelectionText(points.getFirst(), points.getSecond(), buffer);
    }
    finally {
      buffer.unlock();
    }
  }

  public @NotNull String getText() {
    return getText(getTerminalPanel());
  }

  public @Nullable String getSelectedText() {
    return getSelectedText(getTerminalPanel());
  }

  static @NotNull String getText(@NotNull TerminalPanel terminalPanel) {
    TerminalTextBuffer buffer = terminalPanel.getTerminalTextBuffer();
    buffer.lock();
    try {
      TerminalSelection selection = new TerminalSelection(
        new Point(0, -buffer.getHistoryLinesCount()),
        new Point(buffer.getWidth(), buffer.getScreenLinesCount() - 1));
      Pair<Point, Point> points = selection.pointsForRun(buffer.getWidth());
      return SelectionUtil.getSelectionText(points.getFirst(), points.getSecond(), buffer);
    }
    finally {
      buffer.unlock();
    }
  }

  public void writePlainMessage(@NotNull @Nls String message) {
    String str = StringUtil.convertLineSeparators(message, LineSeparator.LF.getSeparatorString());
    List<String> lines = StringUtil.split(str, LineSeparator.LF.getSeparatorString(), true, false);
    boolean first = true;
    for (String line : lines) {
      if (!first) {
        myTerminal.carriageReturn();
        myTerminal.newLine();
      }
      myTerminal.writeCharacters(line);
      first = false;
    }
  }

  public @NotNull TerminalTitle getTerminalTitle() {
    return myTerminalTitle;
  }

  protected void executeCommand(@NotNull String shellCommand) throws IOException {
    throw new RuntimeException("Should be called for ShellTerminalWidget only");
  }

  protected @Nullable List<String> getShellCommand() {
    throw new RuntimeException("Should be called for ShellTerminalWidget only");
  }

  protected void setShellCommand(@Nullable List<String> command) {
    throw new RuntimeException("Should be called for ShellTerminalWidget only");
  }

  /**
   * @throws IllegalStateException of it fails to determine whether the command is running or not.
   */
  protected boolean hasRunningCommands() throws IllegalStateException {
    return false;
  }

  protected @Nullable String getCurrentDirectory() {
    return null;
  }

  private final TerminalWidgetBridge myBridge = new TerminalWidgetBridge();

  public @NotNull TerminalWidget asNewWidget() {
    return myBridge;
  }

  public static @Nullable JBTerminalWidget asJediTermWidget(@NotNull TerminalWidget widget) {
    return widget instanceof TerminalWidgetBridge bridge ? bridge.widget() : null;
  }

  private final class TerminalWidgetBridge implements TerminalWidget {

    private final TtyConnectorAccessor myTtyConnectorAccessor = new TtyConnectorAccessor();

    private @NotNull JBTerminalWidget widget() {
      return JBTerminalWidget.this;
    }

    @Override
    public @NotNull JComponent getComponent() {
      return widget().getComponent();
    }

    @Override
    public JComponent getPreferredFocusableComponent() {
      return widget().getPreferredFocusableComponent();
    }

    @Override
    public @NotNull TerminalTitle getTerminalTitle() {
      return widget().myTerminalTitle;
    }

    @Override
    public void connectToTty(@NotNull TtyConnector ttyConnector, @NotNull TermSize initialTermSize) {
      widget().getTerminal().resize(initialTermSize, RequestOrigin.User);
      widget().createTerminalSession(ttyConnector);
      widget().start();
      widget().getComponent().revalidate();
      widget().notifyStarted();
      myTtyConnectorAccessor.setTtyConnector(ttyConnector);
    }

    @Override
    public @Nullable TerminalSession getSession() {
      return null;
    }

    @Override
    public void connectToSession(@NotNull TerminalSession session) {
      throw new IllegalStateException("TerminalSession is not supported in TerminalWidgetBridge");
    }

    @Override
    public @Nullable TermSize getTermSize() {
      return widget().getTerminalPanel().getTerminalSizeFromComponent();
    }

    @Override
    public void writePlainMessage(@NotNull @Nls String message) {
      widget().writePlainMessage(message);
    }

    @Override
    public void setCursorVisible(boolean visible) {
      ApplicationManager.getApplication().invokeLater(() -> {
        widget().getTerminalPanel().setCursorVisible(false);
      }, myProject.getDisposed());
    }

    @Override
    public void dispose() {
      // JBTerminalWidget should be registered in Disposer independently
    }

    @Override
    public @NotNull TtyConnectorAccessor getTtyConnectorAccessor() {
      return myTtyConnectorAccessor;
    }

    @Override
    public boolean hasFocus() {
      return widget().getTerminalPanel().hasFocus();
    }

    @Override
    public void requestFocus() {
      IdeFocusManager.getInstance(myProject).requestFocus(widget().getTerminalPanel(), true);
    }

    @Override
    public void addTerminationCallback(@NotNull Runnable onTerminated, @NotNull Disposable parentDisposable) {
      TerminalWidgetListener listener = new TerminalWidgetListener() {
        @Override
        public void allSessionsClosed(com.jediterm.terminal.ui.TerminalWidget widget) {
          onTerminated.run();
        }
      };
      widget().addListener(listener);
      Disposer.register(parentDisposable, () -> {
        widget().removeListener(listener);
      });
    }

    @Override
    public void addNotification(@NotNull JComponent notificationComponent, @NotNull Disposable disposable) {
      widget().add(notificationComponent, BorderLayout.NORTH);
      Disposer.register(disposable, () -> {
        widget().remove(notificationComponent);
        widget().revalidate();
      });
    }

    @Override
    public void sendCommandToExecute(@NotNull String shellCommand) {
      try {
        widget().executeCommand(shellCommand);
      }
      catch (IOException e) {
        LOG.info("Cannot execute shell command: " + shellCommand);
      }
    }

    @Override
    public @NotNull CharSequence getText() {
      return widget().getText();
    }

    @Override
    public boolean isCommandRunning() {
      try {
        return widget().hasRunningCommands();
      }
      catch (IllegalStateException e) {
        return true;
      }
    }

    @Override
    public @Nullable String getCurrentDirectory() {
      return widget().getCurrentDirectory();
    }

    @Override
    public @Nullable List<String> getShellCommand() {
      return widget().getShellCommand();
    }

    @Override
    public void setShellCommand(@Nullable List<String> command) {
      widget().setShellCommand(command);
    }

    @Override
    public @NotNull CompletableFuture<@NotNull TermSize> getTerminalSizeInitializedFuture() {
      throw new IllegalStateException("getTerminalSizeInitializedFuture is not supported in TerminalWidgetBridge");
    }
  }
}
