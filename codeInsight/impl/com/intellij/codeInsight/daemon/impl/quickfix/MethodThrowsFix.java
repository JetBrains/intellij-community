package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class MethodThrowsFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.MethodThrowsFix");

  private final PsiMethod myMethod;
  private final PsiClassType myThrowsClassType;
  private final boolean myShouldThrow;
  private final boolean myShowContainingClass;

  public MethodThrowsFix(PsiMethod method, PsiClassType exceptionClass, boolean shouldThrow, boolean showContainingClass) {
    myMethod = method;
    myThrowsClassType = exceptionClass;
    myShouldThrow = shouldThrow;
    myShowContainingClass = showContainingClass;
  }

  @NotNull
  public String getText() {
    String methodName = PsiFormatUtil.formatMethod(myMethod,
                                                   PsiSubstitutor.EMPTY,
                                                   PsiFormatUtil.SHOW_NAME | (myShowContainingClass ? PsiFormatUtil.SHOW_CONTAINING_CLASS: 0),
                                                   0);
    return QuickFixBundle.message(myShouldThrow ? "fix.throws.list.add.exception" : "fix.throws.list.remove.exception",
                                  myThrowsClassType.getCanonicalText(),
                                  methodName);
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("fix.throws.list.family");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return myMethod != null
        && myMethod.isValid()
        && myMethod.getManager().isInProject(myMethod)
        && myThrowsClassType != null
        && myThrowsClassType.isValid();
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(myMethod.getContainingFile())) return;
    PsiJavaCodeReferenceElement[] referenceElements = myMethod.getThrowsList().getReferenceElements();
    try {
      boolean alreadyThrows = false;
      for (PsiJavaCodeReferenceElement referenceElement : referenceElements) {
        if (referenceElement.getCanonicalText().equals(myThrowsClassType.getCanonicalText())) {
          alreadyThrows = true;
          if (!myShouldThrow) {
            referenceElement.delete();
            break;
          }
        }
      }
      if (myShouldThrow && !alreadyThrows) {
        myMethod.getThrowsList().add(myMethod.getManager().getElementFactory().createReferenceElementByType(myThrowsClassType));
      }
      UndoManager.getInstance(file.getProject()).markDocumentForUndo(file);
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public boolean startInWriteAction() {
    return true;
  }

}
