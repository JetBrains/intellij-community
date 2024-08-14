// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.diagnostic.logging;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.impl.ConsoleBuffer;
import com.intellij.execution.process.AnsiEscapeDecoder;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbar;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.FilterComponent;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.ui.components.panels.FlowLayoutWrapper;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.util.List;
import java.util.function.BiConsumer;

public abstract class LogConsoleBase extends AdditionalTabComponent implements LogConsole, LogFilterListener {
  private static final Logger LOG = Logger.getInstance(LogConsoleBase.class);

  private final JPanel mySearchComponent;
  private final JComboBox<LogFilter> myLogFilterCombo;
  private final JPanel myTextFilterWrapper;

  private volatile boolean myDisposed;
  private ConsoleView myConsole;
  private final LightProcessHandler myProcessHandler = new LightProcessHandler();
  private ReaderThread myReaderThread;
  private StringBuffer myOriginalDocument = null;
  private String myLineUnderSelection = null;
  private int myLineOffset = -1;
  private final Project myProject;
  private @NlsContexts.TabTitle String myTitle = null;
  private boolean myWasInitialized;
  private ActionGroup myActions;
  private final boolean myBuildInActions;
  private LogFilterModel myModel;
  private final LogFormatter myFormatter;

  private final List<LogConsoleListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final List<? extends LogFilter> myFilters;

  private FilterComponent myFilter = new FilterComponent("LOG_FILTER_HISTORY", 5) {
    @Override
    public void filter() {
      final Task.Backgroundable task = new Task.Backgroundable(myProject, getApplyingFilterTitle()) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          myModel.updateCustomFilter(getFilter());
        }
      };
      ProgressManager.getInstance().run(task);
    }
  };

  public static @NlsContexts.ProgressTitle @NotNull String getApplyingFilterTitle() {
    return ExecutionBundle.message("progress.title.applying.filter");
  }

  public LogConsoleBase(@NotNull Project project, @Nullable Reader reader, @NlsContexts.TabTitle String title, final boolean buildInActions, LogFilterModel model) {
    this(project, reader, title, buildInActions, model, GlobalSearchScope.allScope(project));
  }

  public LogConsoleBase(@NotNull Project project, @Nullable Reader reader, @NlsContexts.TabTitle String title, final boolean buildInActions, LogFilterModel model,
                        @NotNull GlobalSearchScope scope){
    this(project, reader, title, buildInActions, model, scope, new DefaultLogFormatter());
  }

  public LogConsoleBase(@NotNull Project project, @Nullable Reader reader, @NlsContexts.TabTitle String title, final boolean buildInActions, LogFilterModel model,
                        @NotNull GlobalSearchScope scope, LogFormatter formatter) {
    super(new BorderLayout());
    myProject = project;
    myTitle = title;
    myModel = model;
    myFormatter = formatter;
    myFilters = myModel.getLogFilters();
    myReaderThread = new ReaderThread(reader);
    myBuildInActions = buildInActions;
    TextConsoleBuilder builder = TextConsoleBuilderFactory.getInstance().createBuilder(project, scope);
    myConsole = builder.getConsole();
    myConsole.attachToProcess(myProcessHandler);
    myDisposed = false;
    myModel.addFilterListener(this);

    mySearchComponent = new NonOpaquePanel(new BorderLayout());
    myLogFilterCombo = new ComboBox<>();
    myLogFilterCombo.setOpaque(false);
    myTextFilterWrapper = new NonOpaquePanel();

    mySearchComponent.add(myLogFilterCombo, BorderLayout.WEST);
    mySearchComponent.add(myTextFilterWrapper, BorderLayout.CENTER);
  }

  @Override
  public void setFilterModel(LogFilterModel model) {
    if (myModel != null) {
      myModel.removeFilterListener(this);
    }
    myModel = model;
    myModel.addFilterListener(this);
  }

  @Override
  public LogFilterModel getFilterModel() {
    return myModel;
  }

  protected @Nullable BufferedReader updateReaderIfNeeded(@Nullable BufferedReader reader) throws IOException {
    return reader;
  }

  private void registerShiftTab() {
    // Don't override Shift-TAB if screen reader is active. It is unclear why overriding
    // Shift-TAB was necessary in the first place.
    // See https://github.com/JetBrains/intellij-community/commit/a36a3a00db97e4d5b5c112bb4136a41d9435f667
    if (!ScreenReader.isActive()) {
      new AnAction() {
        {
          var shiftTabShortcut = new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK));
          registerCustomShortcutSet(shiftTabShortcut, LogConsoleBase.this);
        }

        @Override
        public void actionPerformed(final @NotNull AnActionEvent e) {
          var console = ComponentUtil.getParentOfType(ConsoleWithFloatingToolbar.class, getConsoleNotNull().getComponent());
          if (console != null) {
            console.myFloatingToolbar.scheduleShow();
          }
          getTextFilterComponent().requestFocusInWindow();
        }
      };
    }
  }

  public ActionGroup getOrCreateActions() {
    if (myActions != null) return myActions;
    DefaultActionGroup group = new DefaultActionGroup();

    final AnAction[] actions = getConsoleNotNull().createConsoleActions();
    for (AnAction action : actions) {
      group.add(action);
    }

    group.addSeparator();

    /*for (final LogFilter filter : filters) {
      group.add(new ToggleAction(filter.getName(), filter.getName(), filter.getIcon()) {
        public boolean isSelected(AnActionEvent e) {
          return prefs.isFilterSelected(filter);
        }

        public void setSelected(AnActionEvent e, boolean state) {
          prefs.setFilterSelected(filter, state);
        }
      });
    }*/

    myActions = group;

    return myActions;
  }

  @Override
  public void onFilterStateChange(final @NotNull LogFilter filter) {
    filterConsoleOutput();
  }

  @Override
  public void onTextFilterChange() {
    filterConsoleOutput();
  }

  @Override
  public @NotNull JComponent getComponent() {
    if (!myWasInitialized) {
      myWasInitialized = true;
      var console = getConsoleNotNull().getComponent();
      if (myBuildInActions) {
        var search = getSearchComponent();
        var group = getOrCreateActions();
        if (search != null) {
          group = addSearchFilter(group, search);
        }
        add(new ConsoleWithFloatingToolbar(console, group, this), BorderLayout.CENTER);
      } else {
        add(console, BorderLayout.CENTER);
      }
      registerShiftTab();
    }
    return this;
  }

  private ActionGroup addSearchFilter(ActionGroup origin, @NotNull JComponent searchComponent) {
    var filterAction = new ToggleSearchFilterAction() {
      @Override
      protected @NotNull JComponent getSearchFilterComponent() {
        return searchComponent;
      }

      @Override
      protected boolean isModified(@NotNull JComponent component) {
        if (myLogFilterCombo.getSelectedIndex() > 0) {
          return true;
        }
        var textFilterComponent = getTextFilterComponent();
        if (textFilterComponent instanceof FilterComponent) {
          String filterText = ((FilterComponent)textFilterComponent).getFilter();
          return StringUtil.isNotEmpty(filterText);
        }
        return false;
      }
    };
    return new DefaultActionGroup(origin, filterAction);
  }

  private static final class ConsoleWithFloatingToolbar extends JBLayeredPane {
    private static final int TOP_OFFSET = 25;
    private static final int RIGHT_OFFSET = 20;

    private final @NotNull JComponent myComponent;
    private final @NotNull FloatingToolbar myFloatingToolbar;

    private ConsoleWithFloatingToolbar(@NotNull JComponent component, @NotNull ActionGroup actions, @NotNull Disposable disposable) {
      myComponent = component;
      myFloatingToolbar = new FloatingToolbar(component, actions, disposable);

      add(myComponent, JLayeredPane.DEFAULT_LAYER);
      add(myFloatingToolbar, JLayeredPane.POPUP_LAYER);
    }

    @Override
    public void doLayout() {
      Rectangle bounds = getBounds();
      myComponent.setBounds(0, 0, bounds.width, bounds.height);
      var toolbarSize = myFloatingToolbar.getPreferredSize();
      myFloatingToolbar.setBounds(
        bounds.width - toolbarSize.width - RIGHT_OFFSET,
        TOP_OFFSET - (toolbarSize.height - ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.height) / 2,
        toolbarSize.width,
        toolbarSize.height
      );
    }
  }

  private abstract static class ToggleSearchFilterAction extends ToggleAction implements CustomComponentAction {
    ToggleSearchFilterAction() {
      super(() -> ExecutionBundle.message("log.toggle.filter.component"), new LayeredIcon(AllIcons.General.Filter, null));
    }

    protected abstract @NotNull JComponent getSearchFilterComponent();

    protected boolean isModified(@NotNull JComponent component) {
      return false;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return Toggleable.isSelected(e.getPresentation());
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      Toggleable.setSelected(e.getPresentation(), state);
    }

    @Override
    public @NotNull JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
      var button = new ActionButton(this, presentation, "LogSearchFilterToolbar", ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
      var panel = new NonOpaquePanel();
      panel.setBorder(new JBEmptyBorder(0, 2, 0, 2));
      panel.setLayout(new HorizontalLayout(4, SwingConstants.CENTER));
      panel.add(getSearchFilterComponent());
      panel.add(new FlowLayoutWrapper(button));
      return panel;
    }

    @Override
    public void updateCustomComponent(@NotNull JComponent component,
                                      @NotNull Presentation presentation) {
      component.setVisible(presentation.isVisible());
      component.setEnabled(presentation.isEnabled());
      getSearchFilterComponent().setVisible(Toggleable.isSelected(presentation));
      var icon = presentation.getIcon();
      if (icon instanceof LayeredIcon && ((LayeredIcon)icon).getIconCount() == 2) {
        ((LayeredIcon)icon).setIcon(isModified(component) ? AllIcons.Nodes.TabAlert : null, 1);
        presentation.setIcon(icon);
      }
    }
  }

  public abstract boolean isActive();

  public void activate() {
    final ReaderThread readerThread = myReaderThread;
    if (readerThread == null) {
      return;
    }
    if (isActive() && !readerThread.myRunning) {
      resetLogFilter();
      myFilter.setSelectedItem(myModel.getCustomFilter());
      readerThread.startRunning();
      ApplicationManager.getApplication().executeOnPooledThread(readerThread);
    }
    else if (!isActive() && readerThread.myRunning) {
      readerThread.stopRunning();
    }
  }

  @Override
  public @NotNull String getTabTitle() {
    return myTitle;
  }

  @Override
  public void dispose() {
    myModel.removeFilterListener(this);
    stopRunning(false);
    if (myDisposed) return;
    myDisposed = true;
    Disposer.dispose(myConsole);
    myConsole = null;
    myFilter.dispose();
    myFilter = null;
    myOriginalDocument = null;
  }

  private void stopRunning(boolean checkActive) {
    if (!checkActive) {
      fireLoggingWillBeStopped();
    }

    final ReaderThread readerThread = myReaderThread;
    if (readerThread != null && readerThread.myReader != null) {
      if (!checkActive) {
        readerThread.stopRunning();
        try {
          readerThread.myReader.close();
        }
        catch (IOException e) {
          LOG.warn(e);
        }
        readerThread.myReader = null;
        myReaderThread = null;
      }
      else {
        try {
          final BufferedReader reader = readerThread.myReader;
          while (reader.ready()) {
            addMessage(reader.readLine());
          }
        }
        catch (IOException ignore) {}
        stopRunning(false);
      }
    }
  }

  protected void addMessage(final String text) {
    if (myDisposed) return;
    if (text == null) return;
    final LogFilterModel.MyProcessingResult processingResult = myModel.processLine(text);
    if (processingResult.isApplicable()) {
      final Key key = processingResult.getKey();
      if (key != null) {
        final String messagePrefix = processingResult.getMessagePrefix();
        if (messagePrefix != null) {
          String formattedPrefix = myFormatter.formatPrefix(messagePrefix);
          myProcessHandler.notifyTextAvailable(formattedPrefix, key);
        }
        String formattedMessage = myFormatter.formatMessage(text);
        myProcessHandler.notifyTextAvailable(formattedMessage + "\n", key);
      }
    }
    myOriginalDocument = getOriginalDocument();
    if (myOriginalDocument != null) {
      myOriginalDocument.append(text).append("\n");
    }
  }

  public void attachStopLogConsoleTrackingListener(final ProcessHandler process) {
    if (process != null) {
      final ProcessAdapter stopListener = new ProcessAdapter() {
        @Override
        public void processTerminated(final @NotNull ProcessEvent event) {
          process.removeProcessListener(this);
          WriteIntentReadAction.run((Runnable)() ->stopRunning(true));
        }
      };
      process.addProcessListener(stopListener);
    }
  }

  @CalledInAny
  public @Nullable StringBuffer getOriginalDocument() {
    if (myOriginalDocument == null) {
      Editor editor = getEditor();
      if (editor != null) {
        myOriginalDocument = new StringBuffer(editor.getDocument().getText());
      }
    }
    else {
      if (ConsoleBuffer.useCycleBuffer()) {
        resizeBuffer(myOriginalDocument, ConsoleBuffer.getCycleBufferSize());
      }
    }
    return myOriginalDocument;
  }

  static void resizeBuffer(@NotNull StringBuffer buffer, int size) {
    final int toRemove = buffer.length() - size;
    if (toRemove > 0) {

      int indexOfNewline = buffer.indexOf("\n", toRemove);

      if (indexOfNewline == -1) {
        buffer.delete(0, toRemove);
      }
      else {
        buffer.delete(0, indexOfNewline + 1);
      }
    }

  }

  @CalledInAny
  private @Nullable Editor getEditor() {
    ConsoleView console = getConsole();
    if (console == null) return null;
    // TODO This is a hack to get it working in BGT without a proper BGT-enabled document getter
    DataContext dataContext = DataManager.getInstance().customizeDataContext(
      DataContext.EMPTY_CONTEXT, console);
    return CommonDataKeys.EDITOR.getData(dataContext);
  }

  private void filterConsoleOutput() {
    ApplicationManager.getApplication().invokeLater(() -> computeSelectedLineAndFilter());
  }

  private void computeSelectedLineAndFilter() {
    // we have to do this in dispatch thread, because ConsoleViewImpl can flush something to document otherwise
    myOriginalDocument = getOriginalDocument();
    if (myOriginalDocument != null) {
      final Editor editor = getEditor();
      LOG.assertTrue(editor != null);
      final Document document = editor.getDocument();
      final int caretOffset = editor.getCaretModel().getOffset();
      myLineUnderSelection = null;
      myLineOffset = -1;
      if (caretOffset > -1 && caretOffset < document.getTextLength()) {
        int line;
        try {
          line = document.getLineNumber(caretOffset);
        }
        catch (IllegalStateException e) {
          throw new IllegalStateException("document.length=" + document.getTextLength() + ", caret offset = " + caretOffset + "; " + e.getMessage(), e);
        }
        if (line > -1 && line < document.getLineCount()) {
          final int startOffset = document.getLineStartOffset(line);
          myLineUnderSelection = document.getText().substring(startOffset, document.getLineEndOffset(line));
          myLineOffset = caretOffset - startOffset;
        }
      }
    }
    ApplicationManager.getApplication().executeOnPooledThread(() -> doFilter());
  }

  private void doFilter() {
    if (myDisposed) {
      return;
    }
    final ConsoleView console = getConsoleNotNull();
    console.clear();
    myModel.processingStarted();

    final String[] lines = myOriginalDocument != null ? myOriginalDocument.toString().split("\n") : ArrayUtilRt.EMPTY_STRING_ARRAY;
    int offset = 0;
    boolean caretPositioned = false;
    AnsiEscapeDecoder decoder = new AnsiEscapeDecoder();

    for (String line : lines) {
      @SuppressWarnings("CodeBlock2Expr")
      final int printed = printMessageToConsole(line, (text, key) -> {
        decoder.escapeText(text, key, (chunk, attributes) -> {
          console.print(chunk, ConsoleViewContentType.getConsoleViewType(attributes));
        });
      });
      if (printed > 0) {
        if (!caretPositioned) {
          if (Comparing.strEqual(myLineUnderSelection, line)) {
            caretPositioned = true;
            offset += myLineOffset != -1 ? myLineOffset : 0;
          }
          else {
            offset += printed;
          }
        }
      }
    }

    // we need this, because, document can change before actual scrolling, so offset may be already not at the end
    if (caretPositioned) {
      console.scrollTo(offset);
    }
    else {
      console.requestScrollingToEnd();
    }
  }

  private int printMessageToConsole(@NotNull String line, @NotNull BiConsumer<? super String, ? super Key> printer) {
    final LogFilterModel.MyProcessingResult processingResult = myModel.processLine(line);
    if (processingResult.isApplicable()) {
      final Key key = processingResult.getKey();
      if (key != null) {
        final String messagePrefix = processingResult.getMessagePrefix();
        if (messagePrefix != null) {
          printer.accept(myFormatter.formatPrefix(messagePrefix), key);
        }
        printer.accept(myFormatter.formatMessage(line) + "\n", key);
        return (messagePrefix != null ? messagePrefix.length() : 0) + line.length() + 1;
      }
    }
    return 0;
  }

  public @Nullable ConsoleView getConsole() {
    return myConsole;
  }

  /**
   * A shortcut for "getConsole()+assert console != null"
   * Use this method when you are sure that console must not be null.
   * If we get the assertion then it is a time to revisit logic of caller ;)
   */

  private @NotNull ConsoleView getConsoleNotNull() {
    final ConsoleView console = getConsole();
    assert console != null: "it looks like console has been disposed";
    return console;
  }

  @Override
  public ActionGroup getToolbarActions() {
    return getOrCreateActions();
  }

  @Override
  public String getToolbarPlace() {
    return ActionPlaces.UNKNOWN;
  }

  @Override
  public @Nullable JComponent getToolbarContextComponent() {
    final ConsoleView console = getConsole();
    return console == null ? null : console.getComponent();
  }

  @Override
  public JComponent getPreferredFocusableComponent() {
    return getConsoleNotNull().getPreferredFocusableComponent();
  }

  public String getTitle() {
    return myTitle;
  }

  public void clear() {
    getConsoleNotNull().clear();
    myOriginalDocument = null;
  }

  @Override
  public JComponent getSearchComponent() {
    myLogFilterCombo.setModel(new DefaultComboBoxModel<>(myFilters.toArray(new LogFilter[0])));
    resetLogFilter();
    myLogFilterCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final LogFilter filter = (LogFilter)myLogFilterCombo.getSelectedItem();
        final Task.Backgroundable task = new Task.Backgroundable(myProject, getApplyingFilterTitle()) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            myModel.selectFilter(filter);
          }
        };
        ProgressManager.getInstance().run(task);
      }
    });
    AccessibleContextUtil.setName(myLogFilterCombo, ExecutionBundle.message("log.filter.combo.accessible.name"));
    myTextFilterWrapper.removeAll();
    myTextFilterWrapper.add(getTextFilterComponent());
    return mySearchComponent;
  }

  private void resetLogFilter() {
    for (LogFilter filter : myFilters) {
      if (myModel.isFilterSelected(filter)) {
        if (myLogFilterCombo.getSelectedItem() != filter) {
          myLogFilterCombo.setSelectedItem(filter);
          break;
        }
      }
    }
  }

  protected @NotNull Component getTextFilterComponent() {
    return myFilter;
  }

  @Override
  public boolean isContentBuiltIn() {
    return myBuildInActions;
  }

  public void writeToConsole(String text, Key outputType) {
    myProcessHandler.notifyTextAvailable(text, outputType);
  }

  public void addListener(LogConsoleListener listener) {
    myListeners.add(listener);
  }

  private void fireLoggingWillBeStopped() {
    for (LogConsoleListener listener : myListeners) {
      listener.loggingWillBeStopped();
    }
  }

  private static final class LightProcessHandler extends ProcessHandler {

    private final AnsiEscapeDecoder myDecoder = new AnsiEscapeDecoder();

    @Override
    public void notifyTextAvailable(@NotNull String text, @NotNull Key outputType) {
      myDecoder.escapeText(text, outputType, (chunk, attributes) -> super.notifyTextAvailable(chunk, attributes));
    }

    @Override
    protected void destroyProcessImpl() {
      throw new UnsupportedOperationException();
    }

    @Override
    protected void detachProcessImpl() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean detachIsDefault() {
      return false;
    }

    @Override
    public @Nullable OutputStream getProcessInput() {
      return null;
    }
  }

  private final class ReaderThread implements Runnable {
    private BufferedReader myReader;
    private boolean myRunning = false;
    private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, LogConsoleBase.this);

    ReaderThread(@Nullable Reader reader) {
      myReader = reader != null ? new BufferedReader(reader) : null;
    }

    @Override
    public void run() {
      if (myReader == null) return;
      final Runnable runnable = new Runnable() {
        @Override
        public void run() {
          if (myRunning) {
            try {

              myReader = updateReaderIfNeeded(myReader);

              int i = 0;
              while (i++ < 1000) {
                final BufferedReader reader = myReader;
                if (myRunning && reader != null && reader.ready()) {
                  addMessage(reader.readLine());
                }
                else {
                  break;
                }
              }
            }
            catch (IOException e) {
              LOG.info(e);
              addMessage("I/O Error" + (e.getMessage() != null ? ": " + e.getMessage() : ""));
              return;
            }
          }
          if (myAlarm.isDisposed()) return;
          myAlarm.addRequest(this, 100);
        }
      };
      if (myAlarm.isDisposed()) return;
      myAlarm.addRequest(runnable, 10);
    }

    public void startRunning() {
      myRunning = true;
    }

    public void stopRunning() {
      myRunning = false;
      synchronized (this) {
        notifyAll();
      }
    }
  }
}
