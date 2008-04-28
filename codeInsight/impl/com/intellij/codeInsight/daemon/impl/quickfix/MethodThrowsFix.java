package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class MethodThrowsFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.MethodThrowsFix");

  private final PsiMethod myMethod;
  private final String myThrowsCanonicalText;
  private final boolean myShouldThrow;
  private final boolean myShowContainingClass;

  public MethodThrowsFix(PsiMethod method, PsiClassType exceptionType, boolean shouldThrow, boolean showContainingClass) {
    myMethod = method;
    myThrowsCanonicalText = exceptionType.getCanonicalText();
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
                                  myThrowsCanonicalText,
                                  methodName);
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("fix.throws.list.family");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myMethod != null
        && myMethod.isValid()
        && myMethod.getManager().isInProject(myMethod);
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtilBase.prepareFileForWrite(myMethod.getContainingFile())) return;
    PsiJavaCodeReferenceElement[] referenceElements = myMethod.getThrowsList().getReferenceElements();
    try {
      boolean alreadyThrows = false;
      for (PsiJavaCodeReferenceElement referenceElement : referenceElements) {
        if (referenceElement.getCanonicalText().equals(myThrowsCanonicalText)) {
          alreadyThrows = true;
          if (!myShouldThrow) {
            referenceElement.delete();
            break;
          }
        }
      }
      if (myShouldThrow && !alreadyThrows) {
        final PsiElementFactory factory = JavaPsiFacade.getInstance(myMethod.getProject()).getElementFactory();
        final PsiClassType type = (PsiClassType)factory.createTypeFromText(myThrowsCanonicalText, myMethod);
        PsiJavaCodeReferenceElement ref = factory.createReferenceElementByType(type);
        ref = (PsiJavaCodeReferenceElement)JavaCodeStyleManager.getInstance(project).shortenClassReferences(ref);
        myMethod.getThrowsList().add(ref);
      }
      UndoUtil.markPsiFileForUndo(file);
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public boolean startInWriteAction() {
    return true;
  }

}
