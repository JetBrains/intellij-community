// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal;

import com.google.common.base.Ascii;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.actions.ConsoleActionsPostProcessor;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.InputFilter;
import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.execution.process.*;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ObservableConsoleView;
import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.LineSeparator;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.TerminalStarter;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import com.jediterm.terminal.util.CharUtils;
import com.pty4j.PtyProcess;
import com.pty4j.windows.conpty.WinConPtyProcess;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.terminal.TerminalExecutionConsoleBuilderKt.*;

public class TerminalExecutionConsole implements ConsoleView, ObservableConsoleView {
  private static final Logger LOG = Logger.getInstance(TerminalExecutionConsole.class);

  private final JBTerminalWidget myTerminalWidget;
  private final Project myProject;
  private final AppendableTerminalDataStream myDataStream;
  private final AtomicBoolean myAttachedToProcess = new AtomicBoolean(false);
  private final @NotNull InputFilter myInputMessageFilter;
  private volatile boolean myLastCR = false;
  private final TerminalConsoleContentHelper myContentHelper = new TerminalConsoleContentHelper(this);

  private boolean myEnterKeyDefaultCodeEnabled = true;
  private boolean myConvertLfToCrlfForNonPtyProcess = DEFAULT_CONVERT_LF_TO_CRLF_FOR_PROCESS_WITHOUT_PTY;
  private final AtomicBoolean myFirstOutput = new AtomicBoolean(false);

  /**
   * @deprecated use {@link TerminalExecutionConsoleBuilder} and {@link #attachToProcess(ProcessHandler)} instead
   */
  @Deprecated
  public TerminalExecutionConsole(@NotNull Project project, @Nullable ProcessHandler processHandler) {
    this(project, DEFAULT_INITIAL_TERM_SIZE, createDefaultConsoleSettingsProvider(), processHandler);
  }

  /**
   * @deprecated use {@link TerminalExecutionConsoleBuilder} and {@link #attachToProcess(ProcessHandler)} instead
   */
  @Deprecated
  public TerminalExecutionConsole(@NotNull Project project,
                                  @Nullable ProcessHandler processHandler,
                                  @NotNull JBTerminalSystemSettingsProviderBase settingsProvider) {
    this(project, DEFAULT_INITIAL_TERM_SIZE, settingsProvider, processHandler);
  }

  /**
   * @deprecated use {@link TerminalExecutionConsoleBuilder} and {@link #attachToProcess(ProcessHandler)} instead
   */
  @Deprecated
  public TerminalExecutionConsole(@NotNull Project project, int columns, int lines, @Nullable ProcessHandler processHandler) {
    this(project, new TermSize(columns, lines), createDefaultConsoleSettingsProvider(), processHandler);
  }

  /**
   * @deprecated use {@link TerminalExecutionConsoleBuilder} and {@link #attachToProcess(ProcessHandler)} instead
   */
  @Deprecated
  public TerminalExecutionConsole(@NotNull Project project,
                                  int columns,
                                  int lines,
                                  @Nullable ProcessHandler processHandler,
                                  @NotNull JBTerminalSystemSettingsProviderBase settingsProvider) {
    this(project, new TermSize(columns, lines), settingsProvider, processHandler);
  }

  private TerminalExecutionConsole(
    @NotNull Project project,
    @NotNull TermSize initialTermSize,
    @NotNull JBTerminalSystemSettingsProviderBase settingsProvider,
    @Nullable ProcessHandler processHandler
  ) {
    this(project, initialTermSize, settingsProvider, DEFAULT_CONVERT_LF_TO_CRLF_FOR_PROCESS_WITHOUT_PTY, processHandler);
  }

  TerminalExecutionConsole(
    @NotNull Project project,
    @NotNull TermSize initialTermSize,
    @NotNull JBTerminalSystemSettingsProviderBase settingsProvider,
    boolean convertLfToCrlfForNonPtyProcess,
    @Nullable ProcessHandler processHandler
  ) {
    myProject = project;
    myDataStream = new AppendableTerminalDataStream();
    myTerminalWidget = new ConsoleTerminalWidget(project, initialTermSize.getColumns(), initialTermSize.getRows(), settingsProvider);
    myInputMessageFilter = ConsoleViewUtil.computeInputFilter(this, project, GlobalSearchScope.allScope(project));
    myConvertLfToCrlfForNonPtyProcess = convertLfToCrlfForNonPtyProcess;
    if (processHandler != null) {
      attachToProcess(processHandler);
    }
  }

  public @NotNull JBTerminalWidget getTerminalWidget() {
    return myTerminalWidget;
  }

  private void printText(@NotNull String text, @Nullable ConsoleViewContentType contentType) throws IOException {
    if (LOG.isTraceEnabled()) {
      LOG.trace("[" + Thread.currentThread().getName() + "] Print request received: " + CharUtils.toHumanReadableText(text));
    }
    Color foregroundColor = contentType != null ? contentType.getAttributes().getForegroundColor() : null;
    if (foregroundColor != null) {
      myDataStream.append(encodeColor(foregroundColor));
    }

    myDataStream.append(text);

    if (foregroundColor != null) {
      myDataStream.append((char)Ascii.ESC + "[39m"); //restore default foreground color
    }
    myContentHelper.onContentTypePrinted(text, ObjectUtils.notNull(contentType, ConsoleViewContentType.NORMAL_OUTPUT));

    if (myFirstOutput.compareAndSet(false, true) &&
        contentType == ConsoleViewContentType.SYSTEM_OUTPUT &&
        getProcess() instanceof WinConPtyProcess) {
      moveScreenToScrollbackBufferAndShowAllOutput();
    }
  }

  /**
   * This method should be called after printing system output (command line) and before
   * processing output from the ConPTY process. <p/>
   * ConPTY assumes that the screen buffer is empty and the cursor is at (1,1) position when it starts.
   * However, when a system output is printed, the cursor is moved from the (1,1) position.
   * As ConPTY knows nothing about the printed system output and the changed cursor position,
   * it may redraw the screen on top of the printed system output leading to corrupted output (RIDER-131843, WEB-75542).
   * <a href="https://github.com/microsoft/terminal/issues/919#issuecomment-494600135">More details</a>
   * <br/>
   * To prevent the corrupted output, let's move system output from the screen buffer to the scrollback buffer
   * and move the cursor back to (1,1) position to make ConPTY happy.
   * <br/>
   * However, the command line moved to the scrollback buffer is not visible by default.
   * To ensure that the command output is fully visible, we scroll up programmatically.
   */
  private void moveScreenToScrollbackBufferAndShowAllOutput() throws IOException {
    LOG.trace("Printing command line detected at the beginning of the output, scheduling a scroll command.");
    BoundedRangeModel verticalScrollModel = myTerminalWidget.getTerminalPanel().getVerticalScrollModel();
    verticalScrollModel.addChangeListener(new javax.swing.event.ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        verticalScrollModel.removeChangeListener(this);
        UiNotifyConnector.doWhenFirstShown(myTerminalWidget.getTerminalPanel(), () -> {
          myTerminalWidget.getTerminalPanel().scrollToShowAllOutput();
        });
      }
    });
    // `ESC[2J` moves screen lines to the scrollback buffer
    myDataStream.append("\u001b[2J");
    // `ESC[1;1H` positions the cursor in the top-level corner of the screen buffer
    myDataStream.append("\u001b[1;1H");
  }

  @Override
  public void addChangeListener(@NotNull ChangeListener listener, @NotNull Disposable parent) {
    myContentHelper.addChangeListener(listener, parent);
  }

  @ApiStatus.Internal
  @RequiresEdt
  public void flushImmediately() {
    myContentHelper.flush();
  }

  private static @NotNull String encodeColor(@NotNull Color color) {
    return ((char)Ascii.ESC) + "[" + "38;2;" + color.getRed() + ";" + color.getGreen() + ";" +
           color.getBlue() + "m";
  }

  public @NotNull TerminalExecutionConsole withEnterKeyDefaultCodeEnabled(boolean enterKeyDefaultCodeEnabled) {
    myEnterKeyDefaultCodeEnabled = enterKeyDefaultCodeEnabled;
    return this;
  }

  /**
   * @deprecated use {@link TerminalExecutionConsoleBuilder#convertLfToCrlfForProcessWithoutPty(boolean)} instead
   */
  @Deprecated
  public @NotNull TerminalExecutionConsole withConvertLfToCrlfForNonPtyProcess(boolean convertLfToCrlfForNonPtyProcess) {
    myConvertLfToCrlfForNonPtyProcess = convertLfToCrlfForNonPtyProcess;
    return this;
  }

  @Override
  public void print(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
    // Convert line separators to CRLF to behave like ConsoleViewImpl.
    // For example, stacktraces passed to com.intellij.execution.testframework.sm.runner.SMTestProxy.setTestFailed have
    // only LF line separators on Unix.
    String textCRLF = convertTextToCRLF(text);
    try {
      printText(textCRLF, contentType);
    }
    catch (IOException e) {
      LOG.info(e);
    }
  }

  private @NotNull String convertTextToCRLF(@NotNull String text) {
    if (text.isEmpty()) return text;
    // Handle the case when \r and \n are in different chunks: "text1 \r" and "\n text2"
    boolean preserveFirstLF = text.startsWith(LineSeparator.LF.getSeparatorString()) && myLastCR;
    boolean preserveLastCR = text.endsWith(LineSeparator.CR.getSeparatorString());
    myLastCR = preserveLastCR;
    String textToConvert = text.substring(preserveFirstLF ? 1 : 0, preserveLastCR ? text.length() - 1 : text.length());
    String textCRLF = StringUtil.convertLineSeparators(textToConvert, LineSeparator.CRLF.getSeparatorString());
    if (preserveFirstLF) {
      textCRLF = LineSeparator.LF.getSeparatorString() + textCRLF;
    }
    if (preserveLastCR) {
      textCRLF += LineSeparator.CR.getSeparatorString();
    }
    return textCRLF;
  }

  /**
   * Clears history and screen buffers, positions the cursor at the top left corner.
   */
  @Override
  public void clear() {
    myLastCR = false;
    myTerminalWidget.getTerminalPanel().clearBuffer();
  }

  @Override
  public void scrollTo(int offset) {
  }

  @Override
  public void attachToProcess(@NotNull ProcessHandler processHandler) {
    attachToProcess(processHandler, true);
  }

  /**
   * @param processHandler        ProcessHandler instance wrapping underlying PtyProcess
   * @param attachToProcessOutput true if process output should be printed in the console,
   *                              false if output printing is managed externally, e.g. by testing
   *                              console {@link com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView}
   */
  protected final void attachToProcess(@NotNull ProcessHandler processHandler, boolean attachToProcessOutput) {
    attachToProcess(processHandler,
                    new ProcessHandlerTtyConnector(processHandler, EncodingProjectManager.getInstance(myProject).getDefaultCharset()),
                    attachToProcessOutput);
  }

  /**
   * @param processHandler        ProcessHandler instance wrapping underlying PtyProcess
   * @param ttyConnector          ProcessHandlerTtyConnector instance
   * @param attachToProcessOutput true if process output should be printed in the console,
   *                              false if output printing is managed externally, e.g. by testing
   *                              console {@link com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView}
   */
  public final void attachToProcess(@NotNull ProcessHandler processHandler,
                                    @NotNull TtyConnector ttyConnector,
                                    boolean attachToProcessOutput) {
    if (!myAttachedToProcess.compareAndSet(false, true)) {
      return;
    }
    myTerminalWidget.createTerminalSession(ttyConnector);
    myTerminalWidget.start();
    if (attachToProcessOutput) {
      boolean isProcessWithPty = isProcessWithPty(processHandler);
      if (processHandler instanceof ColoredProcessHandler coloredProcessHandler) {
        coloredProcessHandler.addRawTextListener(new ColoredProcessHandler.RawTextListener() {
          @Override
          public void onRawTextAvailable(@NotNull String text, @NotNull Key<?> outputType) {
            processProcessOutputText(text, outputType, isProcessWithPty);
          }
        });
      }
      else {
        processHandler.addProcessListener(new ProcessListener() {
          @Override
          public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
            processProcessOutputText(event.getText(), outputType, isProcessWithPty);
          }
        });
      }
    }
    processHandler.addProcessListener(new ProcessListener() {
      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        myAttachedToProcess.set(false);
        ApplicationManager.getApplication().invokeLater(() -> {
          myTerminalWidget.getTerminalPanel().setCursorVisible(false);
        }, ModalityState.any());
      }
    });
  }

  private void processProcessOutputText(@NotNull String text, @NotNull Key<?> outputType, boolean isProcessWithPty) {
    try {
      ConsoleViewContentType contentType = null;
      if (outputType != ProcessOutputTypes.STDOUT) {
        contentType = ConsoleViewContentType.getConsoleViewType(outputType);
      }
      if (outputType == ProcessOutputTypes.SYSTEM) {
        text = StringUtil.convertLineSeparators(text, LineSeparator.CRLF.getSeparatorString());
      }
      else if (!isProcessWithPty && myConvertLfToCrlfForNonPtyProcess) {
        text = convertTextToCRLF(text);
      }
      ConsoleViewContentType notNullContentType = ObjectUtils.notNull(contentType, ConsoleViewContentType.NORMAL_OUTPUT);
      List<Pair<String, ConsoleViewContentType>> result = myInputMessageFilter.applyFilter(text, notNullContentType);
      if (result == null) {
        printText(text, contentType);
      }
      else {
        for (Pair<String, ConsoleViewContentType> pair : result) {
          if (pair.first != null) {
            printText(pair.first, ObjectUtils.chooseNotNull(pair.second, contentType));
          }
        }
      }
    }
    catch (IOException e) {
      LOG.info(e);
    }
  }

  private static boolean isProcessWithPty(@NotNull ProcessHandler processHandler) {
    if (processHandler instanceof BaseProcessHandler<?> baseProcessHandler) {
      Process process = baseProcessHandler.getProcess();
      return process instanceof PtyProcess ||
             (process instanceof PtyBasedProcess ptyBasedProcess && ptyBasedProcess.hasPty());
    }
    return false;
  }

  @Override
  public void setOutputPaused(boolean value) {

  }

  @Override
  public boolean isOutputPaused() {
    return false;
  }

  @Override
  public boolean hasDeferredOutput() {
    return false;
  }

  @Override
  public void performWhenNoDeferredOutput(@NotNull Runnable runnable) {
  }

  @Override
  public void setHelpId(@NotNull String helpId) {
  }

  @Override
  public void addMessageFilter(@NotNull Filter filter) {
    myTerminalWidget.addMessageFilter(filter);
  }

  @Override
  public void printHyperlink(@NotNull String hyperlinkText, @Nullable HyperlinkInfo info) {

  }

  @Override
  public int getContentSize() {
    return 0;
  }

  @Override
  public boolean canPause() {
    return false;
  }

  @Override
  public AnAction @NotNull [] createConsoleActions() {
    var result = new AnAction[]{new ScrollToTheEndAction(), new ClearAction()};
    var postProcessors = ConsoleActionsPostProcessor.EP_NAME.getExtensionList();
    for (var postProcessor : postProcessors) {
      result = postProcessor.postProcess(this, result);
    }
    return result;
  }

  @Override
  public void allowHeavyFilters() {
  }

  @Override
  public @NotNull JComponent getComponent() {
    return myTerminalWidget.getComponent();
  }

  @Override
  public JComponent getPreferredFocusableComponent() {
    return myTerminalWidget.getComponent();
  }

  /**
   * Shouldn't be called directly, see {@link Disposable} documentation.
   */
  @Override
  public void dispose() {
    // do nothing
  }

  public static boolean isAcceptable(@NotNull ProcessHandler processHandler) {
    return isProcessWithPty(processHandler);
  }

  private final class ConsoleTerminalWidget extends JBTerminalWidget {
    private ConsoleTerminalWidget(@NotNull Project project, int columns, int lines, @NotNull JBTerminalSystemSettingsProviderBase provider) {
      super(project, columns, lines, provider, TerminalExecutionConsole.this, TerminalExecutionConsole.this);
    }

    @Override
    protected JBTerminalPanel createTerminalPanel(@NotNull SettingsProvider settingsProvider,
                                                  @NotNull StyleState styleState,
                                                  @NotNull TerminalTextBuffer textBuffer) {
      JBTerminalPanel panel = new JBTerminalPanel((JBTerminalSystemSettingsProviderBase)settingsProvider, textBuffer, styleState) {
        @Override
        public void clearBuffer() {
          super.clearBuffer(false);
        }
      };

      Disposer.register(this, panel);
      return panel;
    }

    @Override
    protected TerminalStarter createTerminalStarter(@NotNull JediTerminal terminal, @NotNull TtyConnector connector) {
      return new TerminalStarter(terminal, connector, myDataStream, myTerminalWidget.getTypeAheadManager(), getExecutorServiceManager()) {
        @Override
        public byte[] getCode(int key, int modifiers) {
          if (key == KeyEvent.VK_ENTER && modifiers == 0 && myEnterKeyDefaultCodeEnabled) {
            PtyProcess process = ObjectUtils.tryCast(getProcess(), PtyProcess.class);
            return process != null ? new byte[]{process.getEnterKeyCode()} : LineSeparator.CR.getSeparatorBytes();
          }
          return super.getCode(key, modifiers);
        }
      };
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      super.uiDataSnapshot(sink);
      sink.set(LangDataKeys.CONSOLE_VIEW, TerminalExecutionConsole.this);
    }
  }

  private @Nullable Process getProcess() {
    ProcessHandlerTtyConnector phc = ObjectUtils.tryCast(myTerminalWidget.getTtyConnector(), ProcessHandlerTtyConnector.class);
    return phc != null ? phc.getProcess() : null;
  }

  private final class ClearAction extends DumbAwareAction {
    private ClearAction() {
      super(ExecutionBundle.messagePointer("clear.all.from.console.action.name"),
            ExecutionBundle.messagePointer("clear.all.from.console.action.text"), AllIcons.Actions.GC);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(true);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      clear();
    }
  }

  private final class ScrollToTheEndAction extends DumbAwareAction {
    private ScrollToTheEndAction() {
      super(ActionsBundle.messagePointer("action.EditorConsoleScrollToTheEnd.text"),
            ActionsBundle.messagePointer("action.EditorConsoleScrollToTheEnd.text"),
            AllIcons.RunConfigurations.Scroll_down);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      BoundedRangeModel verticalScrollModel = myTerminalWidget.getTerminalPanel().getVerticalScrollModel();
      e.getPresentation().setEnabled(verticalScrollModel.getValue() != 0);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myTerminalWidget.getTerminalPanel().getVerticalScrollModel().setValue(0);
    }
  }
}
