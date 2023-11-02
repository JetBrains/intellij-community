// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.console;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupFocusDegree;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ConsoleExecuteAction extends DumbAwareAction {
  static final String CONSOLE_EXECUTE_ACTION_ID = "Console.Execute";

  private final LanguageConsoleView myConsoleView;
  final ConsoleExecuteActionHandler myExecuteActionHandler;
  private final Condition<? super LanguageConsoleView> myEnabledCondition;

  public ConsoleExecuteAction(@NotNull LanguageConsoleView console, @NotNull BaseConsoleExecuteActionHandler executeActionHandler) {
    this(console, executeActionHandler, CONSOLE_EXECUTE_ACTION_ID, Conditions.alwaysTrue());
  }

  ConsoleExecuteAction(@NotNull LanguageConsoleView console, @NotNull ConsoleExecuteActionHandler executeActionHandler, @Nullable Condition<? super LanguageConsoleView> enabledCondition) {
    this(console, executeActionHandler, CONSOLE_EXECUTE_ACTION_ID, enabledCondition);
  }

  public ConsoleExecuteAction(@NotNull LanguageConsoleView console,
                              @NotNull BaseConsoleExecuteActionHandler executeActionHandler,
                              @Nullable Condition<? super LanguageConsoleView> enabledCondition) {
    this(console, executeActionHandler, CONSOLE_EXECUTE_ACTION_ID, enabledCondition);
  }

  public ConsoleExecuteAction(@NotNull LanguageConsoleView consoleView,
                               @NotNull ConsoleExecuteActionHandler executeActionHandler,
                               @NotNull String emptyExecuteActionId,
                               @Nullable Condition<? super LanguageConsoleView> enabledCondition) {
    this(consoleView, executeActionHandler, emptyExecuteActionId, enabledCondition, AllIcons.Actions.Execute);
  }

  public ConsoleExecuteAction(@NotNull LanguageConsoleView consoleView,
                              @NotNull ConsoleExecuteActionHandler executeActionHandler,
                              @NotNull String emptyExecuteActionId,
                              @Nullable Condition<? super LanguageConsoleView> enabledCondition,
                              @NotNull Icon actionIcon) {
    super(actionIcon);

    myConsoleView = consoleView;
    myExecuteActionHandler = executeActionHandler;
    myEnabledCondition = enabledCondition == null ? Conditions.alwaysTrue() : enabledCondition;

    ActionUtil.mergeFrom(this, emptyExecuteActionId);
  }

  @Override
  public final void update(@NotNull AnActionEvent e) {
    EditorEx editor = myConsoleView.getConsoleEditor();
    boolean enabled = !editor.isRendererMode() && isEnabled() &&
                      (myExecuteActionHandler.isEmptyCommandExecutionAllowed() || !StringUtil.isEmptyOrSpaces(editor.getDocument().getCharsSequence()));
    if (enabled) {
      Lookup lookup = LookupManager.getActiveLookup(editor);
      // we should check getCurrentItem() also - fast typing could produce outdated lookup, such lookup reports isCompletion() true
      enabled = lookup == null || !lookup.isCompletion() || lookup.getCurrentItem() == null ||
                lookup instanceof LookupImpl && ((LookupImpl)lookup).getLookupFocusDegree() == LookupFocusDegree.UNFOCUSED;
    }

    e.getPresentation().setEnabled(enabled);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public final void actionPerformed(@NotNull AnActionEvent e) {
    myExecuteActionHandler.runExecuteAction(myConsoleView);
  }

  public boolean isEnabled() {
    return myEnabledCondition.value(myConsoleView);
  }

  public void execute(@Nullable TextRange range, @NotNull String text, @Nullable EditorEx editor) {
    if (range == null) {
      ((LanguageConsoleImpl)myConsoleView).doAddPromptToHistory();
      myConsoleView.print(text, ConsoleViewContentType.USER_INPUT);
      if (!text.endsWith("\n")) {
        myConsoleView.print("\n", ConsoleViewContentType.USER_INPUT);
      }
    }
    else {
      assert editor != null;
      ((LanguageConsoleImpl)myConsoleView).addTextRangeToHistory(range, editor, myExecuteActionHandler.myPreserveMarkup);
    }
    myExecuteActionHandler.addToCommandHistoryAndExecute(myConsoleView, text);
  }

  public abstract static class ConsoleExecuteActionHandler {

    private boolean myAddToHistory = true;
    final boolean myPreserveMarkup;

    boolean myUseProcessStdIn;

    ConsoleExecuteActionHandler(boolean preserveMarkup) {
      myPreserveMarkup = preserveMarkup;
    }

    public boolean isEmptyCommandExecutionAllowed() {
      return true;
    }

    public final void setAddCurrentToHistory(boolean addCurrentToHistory) {
      myAddToHistory = addCurrentToHistory;
    }

    protected void beforeExecution(@NotNull LanguageConsoleView consoleView) {
    }

    public void runExecuteAction(@NotNull LanguageConsoleView consoleView) {
      if (!myUseProcessStdIn) {
        beforeExecution(consoleView);
      }

      String text = ((LanguageConsoleImpl)consoleView).prepareExecuteAction(myAddToHistory && !myUseProcessStdIn,
                                                                                       myPreserveMarkup, true);
      ((UndoManagerImpl)UndoManager.getInstance(consoleView.getProject())).invalidateActionsFor(DocumentReferenceManager.getInstance().create(
        consoleView.getCurrentEditor().getDocument()));

      if (myUseProcessStdIn) {
        consoleView.print(text, ConsoleViewContentType.USER_INPUT);
        consoleView.print("\n", ConsoleViewContentType.USER_INPUT);
      }
      else {
        addToCommandHistoryAndExecute(consoleView, text);
      }
    }

    private void addToCommandHistoryAndExecute(@NotNull LanguageConsoleView consoleView, @NotNull String text) {
      ConsoleHistoryController.addToHistory(consoleView, text);
      doExecute(text, consoleView);
    }

    abstract void doExecute(@NotNull String text, @NotNull LanguageConsoleView consoleView);
  }
}