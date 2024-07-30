// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal;

import com.intellij.execution.filters.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.terminal.actions.TerminalActionUtil;
import com.intellij.terminal.search.DefaultJediTermSearchComponent;
import com.intellij.terminal.search.JediTermSearchComponentProvider;
import com.intellij.terminal.ui.TerminalWidget;
import com.intellij.terminal.ui.TtyConnectorAccessor;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.LineSeparator;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBSwingUtilities;
import com.intellij.util.ui.RegionPainter;
import com.jediterm.core.compatibility.Point;
import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.*;
import com.jediterm.terminal.model.SelectionUtil;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalSelection;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.model.hyperlinks.LinkInfo;
import com.jediterm.terminal.model.hyperlinks.LinkResult;
import com.jediterm.terminal.model.hyperlinks.LinkResultItem;
import com.jediterm.terminal.ui.*;
import com.jediterm.terminal.ui.hyperlinks.LinkInfoEx;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import kotlin.Pair;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.List;

public class JBTerminalWidget extends JediTermWidget implements Disposable, UiCompatibleDataProvider {
  private static final Logger LOG = Logger.getInstance(JBTerminalWidget.class);

  public static final DataKey<JBTerminalWidget> TERMINAL_DATA_KEY = DataKey.create(JBTerminalWidget.class.getName());
  public static final DataKey<String> SELECTED_TEXT_DATA_KEY = DataKey.create(JBTerminalWidget.class.getName() + " selected text");

  private final CompositeFilterWrapper myCompositeFilterWrapper;
  private JBTerminalWidgetListener myListener;
  private final Project myProject;
  private final @NotNull TerminalTitle myTerminalTitle = new TerminalTitle();

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
    myCompositeFilterWrapper = new CompositeFilterWrapper(project, console, this);
    myProject = project;
    addHyperlinkFilter(line -> runFilters(project, line));
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

  private @Nullable LinkResult runFilters(@NotNull Project project, @NotNull String line) {
    Filter.Result r = ReadAction.nonBlocking(() -> {
      if (project.isDisposed()) {
        return null;
      }
      try {
        return myCompositeFilterWrapper.getCompositeFilter().applyFilter(line, line.length());
      }
      catch (ProcessCanceledException e) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Skipping running filters on " + line, e);
        }
        return null;
      }
      catch (CompositeFilter.ApplyFilterException applyFilterException) {
        LOG.error(applyFilterException);
        return null;
      }
    }).executeSynchronously();
    if (r != null) {
      return new LinkResult(ContainerUtil.mapNotNull(r.getResultItems(), item -> convertResultItem(project, item)));
    }
    return null;
  }

  private @Nullable LinkResultItem convertResultItem(@NotNull Project project, @NotNull Filter.ResultItem item) {
    HyperlinkInfo info = item.getHyperlinkInfo();
    if (info != null) {
      return new LinkResultItem(item.getHighlightStartOffset(), item.getHighlightEndOffset(),
                                convertInfo(project, info));
    }
    return null;
  }

  private @NotNull LinkInfo convertInfo(@NotNull Project project, @NotNull HyperlinkInfo info) {
    LinkInfoEx.Builder builder = new LinkInfoEx.Builder().setNavigateCallback(() -> {
      info.navigate(project);
    });
    if (info instanceof HyperlinkWithPopupMenuInfo) {
      builder.setPopupMenuGroupProvider(new LinkInfoEx.PopupMenuGroupProvider() {
        @Override
        public @NotNull List<TerminalAction> getPopupMenuGroup(@NotNull MouseEvent event) {
          ActionGroup group = ((HyperlinkWithPopupMenuInfo)info).getPopupMenuGroup(event);
          AnAction[] actions = group != null ? group.getChildren(null) : AnAction.EMPTY_ARRAY;
          return ContainerUtil.map(actions, action -> TerminalActionUtil.createTerminalAction(JBTerminalWidget.this, action));
        }
      });
    }
    if (info instanceof HyperlinkWithHoverInfo) {
      builder.setHoverConsumer(new LinkInfoEx.HoverConsumer() {
        @Override
        public void onMouseEntered(@NotNull JComponent hostComponent, @NotNull Rectangle linkBounds) {
          ((HyperlinkWithHoverInfo)info).onMouseEntered(hostComponent, linkBounds);
        }

        @Override
        public void onMouseExited() {
          ((HyperlinkWithHoverInfo)info).onMouseExited();
        }
      });
    }
    return builder.build();
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
  public void setTtyConnector(@NotNull TtyConnector ttyConnector) {
    super.setTtyConnector(ttyConnector);
    myTerminalTitle.change(terminalTitleState -> {
      if (terminalTitleState.getDefaultTitle() == null) {
        terminalTitleState.setDefaultTitle(getDefaultSessionName(ttyConnector));
      }
      return null;
    });
  }

  public @Nls @Nullable String getDefaultSessionName(@NotNull TtyConnector connector) {
    return connector.getName(); //NON-NLS
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

  public int getFontSize() {
    return getSettingsProvider().getUiSettingsManager().getFontSize();
  }

  public float getFontSize2D() {
    return getSettingsProvider().getUiSettingsManager().getFontSize2D();
  }

  public void setFontSize(int fontSize) {
    setFontSize((float)fontSize);
  }

  public void setFontSize(float fontSize) {
    getSettingsProvider().getUiSettingsManager().setFontSize(fontSize);
  }

  public void resetFontSize() {
    getSettingsProvider().getUiSettingsManager().resetFontSize();
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
    myCompositeFilterWrapper.addFilter(filter);
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

    @Nullable
    @Override
    public List<String> getShellCommand() {
      return widget().getShellCommand();
    }

    @Override
    public void setShellCommand(@Nullable List<String> command) {
      widget().setShellCommand(command);
    }
  }
}
