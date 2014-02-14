package com.intellij.execution.console;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.EditorGutterAction;
import com.intellij.openapi.editor.TextAnnotationGutterProvider;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class LanguageConsoleBuilder {
  private final LanguageConsoleImpl myConsole;
  private LanguageConsoleView myConsoleView;
  private Condition<LanguageConsoleImpl> myExecutionEnabled = Conditions.alwaysTrue();

  @Nullable
  private TextAnnotationGutterProvider myGutterProvider;
  @Nullable
  private EditorGutterAction myGutterAction;

  public LanguageConsoleBuilder(@NotNull LanguageConsoleView consoleView) {
    myConsole = consoleView.getConsole();
    myConsoleView = consoleView;
  }

  public LanguageConsoleBuilder(@NotNull Project project, @NotNull Language language) {
    myConsole = new LanguageConsoleImpl(project, language.getDisplayName() + " Console", language, false);
  }

  public LanguageConsoleBuilder processHandler(@NotNull ProcessHandler processHandler) {
    myExecutionEnabled = new ProcessBackedExecutionEnabledCondition(processHandler);
    return this;
  }

  public LanguageConsoleBuilder executionEnabled(@NotNull Condition<LanguageConsoleImpl> condition) {
    myExecutionEnabled = condition;
    return this;
  }

  public LanguageConsoleBuilder initActions(@NotNull BaseConsoleExecuteActionHandler executeActionHandler, @NotNull String historyType) {
    ensureConsoleViewCreated();

    ConsoleExecuteAction action = new ConsoleExecuteAction(myConsoleView, executeActionHandler, ConsoleExecuteAction.CONSOLE_EXECUTE_ACTION_ID, myExecutionEnabled);
    action.registerCustomShortcutSet(action.getShortcutSet(), myConsole.getConsoleEditor().getComponent());

    new ConsoleHistoryController(historyType, null, myConsole, executeActionHandler.getConsoleHistoryModel()).install();
    return this;
  }

  public LanguageConsoleBuilder historyAnnotation(@NotNull TextAnnotationGutterProvider provider) {
    myGutterProvider = provider;
    myGutterAction = provider instanceof EditorGutterAction ? (EditorGutterAction)provider : null;
    return this;
  }

  private void ensureConsoleViewCreated() {
    if (myConsoleView == null) {
      myConsoleView = new LanguageConsoleViewImpl(myConsole, true);
    }
  }

  public LanguageConsoleView build() {
    myConsole.setShowSeparatorLine(false);
    myConsole.initComponents();

    if (myGutterProvider != null) {
      EditorEx editor = myConsole.getHistoryViewer();

      JPanel panel = new JPanel(new BorderLayout());
      JScrollPane scrollPane = editor.getScrollPane();
      scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    }

    ensureConsoleViewCreated();
    return myConsoleView;
  }

  public static class ProcessBackedExecutionEnabledCondition implements Condition<LanguageConsoleImpl> {
    private final ProcessHandler myProcessHandler;

    public ProcessBackedExecutionEnabledCondition(ProcessHandler myProcessHandler) {
      this.myProcessHandler = myProcessHandler;
    }

    @Override
    public boolean value(LanguageConsoleImpl console) {
      return !myProcessHandler.isProcessTerminated();
    }
  }
}