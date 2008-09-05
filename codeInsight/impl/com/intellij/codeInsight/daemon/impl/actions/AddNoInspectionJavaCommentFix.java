package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.SuppressManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspMethodCall;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class AddNoInspectionJavaCommentFix extends AddNoInspectionCommentFix {
  public AddNoInspectionJavaCommentFix(HighlightDisplayKey key) {
    super(key, PsiStatement.class);
  }

  @Nullable
  protected PsiElement getContainer(PsiElement context) {
    if (context == null || PsiTreeUtil.getParentOfType(context, JspMethodCall.class) != null) return null;
    return PsiTreeUtil.getParentOfType(context, PsiStatement.class);
  }

  @Override
  protected void createSuppression(final Project project,
                                   final Editor editor,
                                   final PsiElement element,
                                   final PsiElement container) throws IncorrectOperationException {
    boolean added = false;
    if (container instanceof PsiDeclarationStatement && SuppressManager.getInstance().canHave15Suppressions(element)) {
      final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)container;
      final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
      for (PsiElement declaredElement : declaredElements) {
        if (declaredElement instanceof PsiLocalVariable) {
          final PsiModifierList modifierList = ((PsiLocalVariable)declaredElement).getModifierList();
          if (modifierList != null) {
            AddSuppressInspectionFix.addSuppressAnnotation(project, editor, container, modifierList, myID);
            added = true;
            break;
          }
        }
      }
    }
    if (!added) {
      super.createSuppression(project, editor, element, container);
    }
  }
}
