package com.intellij.execution.runners;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.EmptyAction;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

public class ConsoleExecuteAction extends DumbAwareAction {
  public static final String CONSOLE_EXECUTE_ACTION_ID = "Console.Execute";

  private final LanguageConsoleImpl myConsole;
  private final BaseConsoleExecuteActionHandler myExecuteActionHandler;

  public ConsoleExecuteAction(@NotNull LanguageConsoleImpl console, @NotNull BaseConsoleExecuteActionHandler executeActionHandler) {
    this(console, executeActionHandler, CONSOLE_EXECUTE_ACTION_ID);
  }

  public ConsoleExecuteAction(@NotNull LanguageConsoleImpl console, @NotNull BaseConsoleExecuteActionHandler executeActionHandler,
                              @NotNull String emptyExecuteActionId) {
    super(null, null, AllIcons.Actions.Execute);

    myConsole = console;
    myExecuteActionHandler = executeActionHandler;

    EmptyAction.setupAction(this, emptyExecuteActionId, null);
  }

  @Override
  public final void update(AnActionEvent e) {
    EditorEx editor = myConsole.getConsoleEditor();
    Lookup lookup = LookupManager.getActiveLookup(editor);
    e.getPresentation().setEnabled(!editor.isRendererMode() && isEnabled() &&
                                   (lookup == null || !(lookup.isCompletion() && lookup.isFocused())));
  }

  @Override
  public final void actionPerformed(AnActionEvent e) {
    myExecuteActionHandler.runExecuteAction(myConsole);
  }

  protected boolean isEnabled() {
    return true;
  }
}