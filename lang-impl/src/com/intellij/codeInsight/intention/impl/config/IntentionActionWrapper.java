package com.intellij.codeInsight.intention.impl.config;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class IntentionActionWrapper implements IntentionAction {
  private final IntentionAction myDelegate;
  private final String[] myCategories;

  public IntentionActionWrapper(final IntentionAction delegate, String[] categories) {
    myDelegate = delegate;
    myCategories = categories;
  }

  public String getText() {
    return myDelegate.getText();
  }

  public String getFamilyName() {
    return myDelegate.getFamilyName();
  }

  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    return myDelegate.isAvailable(project, editor, file);
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    myDelegate.invoke(project, editor, file);
  }

  public boolean startInWriteAction() {
    return myDelegate.startInWriteAction();
  }

  public String getFullFamilyName(){
    if (myCategories != null) {
      return StringUtil.join(myCategories, "/") + "/" + getFamilyName();
    }
    else {
      return getFamilyName();
    }
  }

  public IntentionAction getDelegate() {
    return myDelegate;
  }
}
