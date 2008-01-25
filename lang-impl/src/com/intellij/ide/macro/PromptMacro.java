package com.intellij.ide.macro;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.ui.Messages;

public final class PromptMacro extends Macro implements SecondQueueExpandMacro {
  public String getName() {
    return "Prompt";
  }

  public String getDescription() {
    return IdeBundle.message("macro.prompt");
  }

  public String expand(DataContext dataContext) throws ExecutionCancelledException {
    String userInput = Messages.showInputDialog(IdeBundle.message("prompt.enter.parameters"),
                                                IdeBundle.message("title.input"), Messages.getQuestionIcon());
    if (userInput == null) throw new ExecutionCancelledException();
    return userInput;
  }

  public void cachePreview(DataContext dataContext) {
    myCachedPreview = IdeBundle.message("macro.prompt.preview");
  }
}
