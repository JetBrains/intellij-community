// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.HyperlinkWithHoverInfo;
import com.intellij.execution.filters.HyperlinkWithPopupMenuInfo;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.terminal.actions.TerminalActionUtil;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.LineSeparator;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBSwingUtilities;
import com.intellij.util.ui.RegionPainter;
import com.jediterm.terminal.ProcessTtyConnector;
import com.jediterm.terminal.SubstringFinder;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.model.SelectionUtil;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalSelection;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.model.hyperlinks.LinkInfo;
import com.jediterm.terminal.model.hyperlinks.LinkResult;
import com.jediterm.terminal.model.hyperlinks.LinkResultItem;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.TerminalAction;
import com.jediterm.terminal.ui.TerminalPanel;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import com.jediterm.terminal.util.Pair;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ItemListener;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.util.List;

public class JBTerminalWidget extends JediTermWidget implements Disposable, DataProvider {
  private static final Logger LOG = Logger.getInstance(JBTerminalWidget.class);

  public static final DataKey<JBTerminalWidget> TERMINAL_DATA_KEY = DataKey.create(JBTerminalWidget.class.getName());
  public static final DataKey<String> SELECTED_TEXT_DATA_KEY = DataKey.create(JBTerminalWidget.class.getName() + " selected text");

  private final CompositeFilterWrapper myCompositeFilterWrapper;
  private JBTerminalWidgetListener myListener;
  private final Project myProject;

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
    setName("terminal");
    Disposer.register(parent, this);
    setFocusTraversalPolicy(new DefaultFocusTraversalPolicy() {
      @Override
      public Component getDefaultComponent(Container aContainer) {
        return getTerminalPanel();
      }
    });
  }

  @Nullable
  private LinkResult runFilters(@NotNull Project project, @NotNull String line) {
    Filter.Result r = ReadAction.compute(() -> {
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
    });
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
    LinkInfo.Builder builder = new LinkInfo.Builder().setNavigateCallback(() -> {
      info.navigate(project);
    });
    if (info instanceof HyperlinkWithPopupMenuInfo) {
      builder.setPopupMenuGroupProvider(new LinkInfo.PopupMenuGroupProvider() {
        @Override
        public @NotNull List<TerminalAction> getPopupMenuGroup(@NotNull MouseEvent event) {
          ActionGroup group = ((HyperlinkWithPopupMenuInfo)info).getPopupMenuGroup(event);
          AnAction[] actions = group != null ? group.getChildren(null) : AnAction.EMPTY_ARRAY;
          return ContainerUtil.map(actions, action -> TerminalActionUtil.createTerminalAction(JBTerminalWidget.this, action));
        }
      });
    }
    if (info instanceof HyperlinkWithHoverInfo) {
      builder.setHoverConsumer(new LinkInfo.HoverConsumer() {
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
          g.setColor(mySettingsProvider.getTerminalColorPalette().getBackground(backgroundColor));
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
  protected SearchComponent createSearchComponent() {
    return new SearchComponent() {
      private final SearchTextField myTextField = new SearchTextField(false);

      @Override
      public String getText() {
        return myTextField.getText();
      }

      @Override
      public boolean ignoreCase() {
        return false;
      }

      @Override
      public JComponent getComponent() {
        myTextField.setOpaque(false);
        return myTextField;
      }

      @Override
      public void addDocumentChangeListener(DocumentListener listener) {
        myTextField.addDocumentListener(listener);
      }

      @Override
      public void addKeyListener(KeyListener listener) {
        myTextField.addKeyboardListener(listener);
      }

      @Override
      public void addIgnoreCaseListener(ItemListener listener) {

      }

      @Override
      public void onResultUpdated(SubstringFinder.FindResult result) {
      }

      @Override
      public void nextFindResultItem(SubstringFinder.FindResult.FindItem item) {
      }

      @Override
      public void prevFindResultItem(SubstringFinder.FindResult.FindItem item) {
      }
    };
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

  public void moveDisposable(@NotNull Disposable newParent) {
    Disposer.register(newParent, this);
  }

  public void notifyStarted() {
    if (myListener != null) {
      myListener.onTerminalStarted();
    }
  }

  public void notifyRenamed() {
    if (myListener != null) {
      myListener.onTerminalRenamed();
    }
  }

  @Nullable
  @Override
  public Object getData(@NotNull String dataId) {
    if (SELECTED_TEXT_DATA_KEY.is(dataId)) {
      return getSelectedText(getTerminalPanel());
    }
    if (TERMINAL_DATA_KEY.is(dataId)) {
      return this;
    }
    return null;
  }

  static @Nullable String getSelectedText(@NotNull TerminalPanel terminalPanel) {
    TerminalSelection selection = terminalPanel.getSelection();
    if (selection == null) return null;
    TerminalTextBuffer buffer = terminalPanel.getTerminalTextBuffer();
    buffer.lock();
    try {
      Pair<Point, Point> points = selection.pointsForRun(terminalPanel.getColumnCount());
      return SelectionUtil.getSelectionText(points.first, points.second, buffer);
    }
    finally {
      buffer.unlock();
    }
  }

  static @NotNull String getText(@NotNull TerminalPanel terminalPanel) {
    TerminalTextBuffer buffer = terminalPanel.getTerminalTextBuffer();
    buffer.lock();
    try {
      TerminalSelection selection = new TerminalSelection(
        new Point(0, -buffer.getHistoryLinesCount()),
        new Point(terminalPanel.getWidth(), buffer.getScreenLinesCount()));
      Pair<Point, Point> points = selection.pointsForRun(terminalPanel.getColumnCount());
      return SelectionUtil.getSelectionText(points.first, points.second, buffer);
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
}
