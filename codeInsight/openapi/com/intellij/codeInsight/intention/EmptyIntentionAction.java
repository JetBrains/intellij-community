package com.intellij.codeInsight.intention;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * User: anna
 * Date: May 11, 2005
 */
public class EmptyIntentionAction implements IntentionAction{
  private String myName;

  @Deprecated
  public EmptyIntentionAction(@NotNull final String name, @NotNull List<IntentionAction> options) {
    myName = name;
  }

  public EmptyIntentionAction(final String name) {
    myName = name;
  }

  @NotNull
  public String getText() {
    return InspectionsBundle.message("inspection.options.action.text", myName);
  }

  @NotNull
  public String getFamilyName() {
    return myName;
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return true; //edit inspection settings is always enabled
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
  }

  public boolean startInWriteAction() {
    return false;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final EmptyIntentionAction that = (EmptyIntentionAction)o;

    if (myName != null ? !myName.equals(that.myName) : that.myName != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (myName != null ? myName.hashCode() : 0);
    return result;
  }

}
