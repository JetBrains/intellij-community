// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal;

import com.google.common.base.Ascii;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.util.LineSeparator;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.jediterm.terminal.HyperlinkStyle;
import com.jediterm.terminal.TerminalStarter;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import com.jediterm.terminal.util.CharUtils;
import com.pty4j.PtyProcess;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class TerminalExecutionConsole implements ConsoleView, ObservableConsoleView {
  private static final Logger LOG = Logger.getInstance(TerminalExecutionConsole.class);
  private static final String MAKE_CURSOR_INVISIBLE = "\u001b[?25l";
  private static final String MAKE_CURSOR_VISIBLE = "\u001b[?25h";
  private static final String CLEAR_SCREEN = "\u001b[2J";

  private final JBTerminalWidget myTerminalWidget;
  private final Project myProject;
  private final AppendableTerminalDataStream myDataStream;
  private final AtomicBoolean myAttachedToProcess = new AtomicBoolean(false);
  private volatile boolean myLastCR = false;
  private final TerminalConsoleContentHelper myContentHelper = new TerminalConsoleContentHelper(this);

  private boolean myEnterKeyDefaultCodeEnabled = true;
  private boolean myConvertLfToCrlfForNonPtyProcess = false;
  private final AtomicBoolean myFirstOutput = new AtomicBoolean(false);

  public TerminalExecutionConsole(@NotNull Project project, @Nullable ProcessHandler processHandler) {
    this(project, processHandler, getProvider());
  }

  public TerminalExecutionConsole(@NotNull Project project,
                                  @Nullable ProcessHandler processHandler,
                                  @NotNull JBTerminalSystemSettingsProviderBase settingsProvider) {
    this(project, 200, 24, processHandler, settingsProvider);
  }

  public TerminalExecutionConsole(@NotNull Project project, int columns, int lines, @Nullable ProcessHandler processHandler) {
    this(project, columns, lines, processHandler, getProvider());
  }

  public TerminalExecutionConsole(@NotNull Project project,
                                  int columns,
                                  int lines,
                                  @Nullable ProcessHandler processHandler,
                                  @NotNull JBTerminalSystemSettingsProviderBase settingsProvider) {
    myProject = project;
    myDataStream = new AppendableTerminalDataStream();
    myTerminalWidget = new ConsoleTerminalWidget(project, columns, lines, settingsProvider);
    if (processHandler != null) {
      attachToProcess(processHandler);
    }
  }

  private static @NotNull JBTerminalSystemSettingsProviderBase getProvider() {
    return new JBTerminalSystemSettingsProviderBase() {
      @Override
      public HyperlinkStyle.HighlightMode getHyperlinkHighlightingMode() {
        return HyperlinkStyle.HighlightMode.ALWAYS;
      }
    };
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

    if (contentType != ConsoleViewContentType.SYSTEM_OUTPUT && myFirstOutput.compareAndSet(false, true) && startsWithClearScreen(text)) {
      LOG.trace("Clear Screen request detected at the beginning of the output, scheduling a scroll command.");
      // Windows ConPTY generates the 'clear screen' escape sequence (ESC[2J) optionally preceded by a "make cursor invisible" (ESC?25l) before the process output.
      // It pushes the already printed command line into the scrollback buffer which is not displayed by default.
      // In such cases, let's scroll up to display the printed command line.
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
    }
    myDataStream.append(text);

    if (foregroundColor != null) {
      myDataStream.append((char)Ascii.ESC + "[39m"); //restore default foreground color
    }
    myContentHelper.onContentTypePrinted(text, ObjectUtils.notNull(contentType, ConsoleViewContentType.NORMAL_OUTPUT));
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

  private static boolean startsWithClearScreen(@NotNull String text) {
    // ConPTY will randomly send these commands at any time, so we should skip them:
    int offset = 0;
    while (text.startsWith(MAKE_CURSOR_INVISIBLE, offset) || text.startsWith(MAKE_CURSOR_VISIBLE, offset)) {
      offset += MAKE_CURSOR_INVISIBLE.length();
    }

    return text.startsWith(CLEAR_SCREEN, offset);
  }

  public @NotNull TerminalExecutionConsole withEnterKeyDefaultCodeEnabled(boolean enterKeyDefaultCodeEnabled) {
    myEnterKeyDefaultCodeEnabled = enterKeyDefaultCodeEnabled;
    return this;
  }

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

    processHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        if (attachToProcessOutput) {
          try {
            ConsoleViewContentType contentType = null;
            if (outputType != ProcessOutputTypes.STDOUT) {
              contentType = ConsoleViewContentType.getConsoleViewType(outputType);
            }

            String text = event.getText();
            if (outputType == ProcessOutputTypes.SYSTEM) {
              text = StringUtil.convertLineSeparators(text, LineSeparator.CRLF.getSeparatorString());
            }
            else if (shouldConvertLfToCrlf(processHandler)) {
              text = convertTextToCRLF(text);
            }
            printText(text, contentType);
          }
          catch (IOException e) {
            LOG.info(e);
          }
        }
      }

      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        myAttachedToProcess.set(false);
        ApplicationManager.getApplication().invokeLater(() -> {
          myTerminalWidget.getTerminalPanel().setCursorVisible(false);
        }, ModalityState.any());
      }
    });
  }

  private boolean shouldConvertLfToCrlf(@NotNull ProcessHandler processHandler) {
    return myConvertLfToCrlfForNonPtyProcess && isNonPtyProcess(processHandler);
  }

  private static boolean isNonPtyProcess(@NotNull ProcessHandler processHandler) {
    if (processHandler instanceof BaseProcessHandler) {
      Process process = ((BaseProcessHandler<?>)processHandler).getProcess();
      return !(process instanceof PtyProcess);
    }
    return true;
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
    return new AnAction[]{new ScrollToTheEndAction(), new ClearAction()};
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

  @Override
  public void dispose() {
  }

  public static boolean isAcceptable(@NotNull ProcessHandler processHandler) {
    if (!(processHandler instanceof OSProcessHandler) || processHandler instanceof ColoredProcessHandler) {
      return false;
    }
    Process process = ((OSProcessHandler)processHandler).getProcess();
    return process instanceof PtyProcess ||
           (process instanceof PtyBasedProcess && ((PtyBasedProcess)process).hasPty());
  }

  private final class ConsoleTerminalWidget extends JBTerminalWidget implements DataProvider {
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
            PtyProcess process = getPtyProcess();
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

  private @Nullable PtyProcess getPtyProcess() {
    ProcessHandlerTtyConnector phc = ObjectUtils.tryCast(myTerminalWidget.getTtyConnector(), ProcessHandlerTtyConnector.class);
    return phc != null ? phc.getPtyProcess() : null;
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
