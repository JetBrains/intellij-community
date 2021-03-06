// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public final class AddExceptionFromFieldInitializerToConstructorThrowsFix extends BaseIntentionAction {
  private final static Logger LOG = Logger.getInstance(AddExceptionFromFieldInitializerToConstructorThrowsFix.class);

  private final PsiElement myWrongElement;

  public AddExceptionFromFieldInitializerToConstructorThrowsFix(PsiElement element) {
    myWrongElement = element;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!myWrongElement.isValid()) return false;
    final NavigatablePsiElement maybeField =
      PsiTreeUtil.getParentOfType(myWrongElement, PsiMethod.class, PsiFunctionalExpression.class, PsiField.class);
    if (!(maybeField instanceof PsiField)) return false;
    final PsiField field = (PsiField)maybeField;
    if (field.hasModifierProperty(PsiModifier.STATIC)) return false;
    final PsiClass containingClass = field.getContainingClass();
    if (containingClass == null ||
        containingClass instanceof PsiAnonymousClass ||
        containingClass.isInterface()) {
      return false;
    }
    final List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(field);
    if (exceptions.isEmpty()) {
      return false;
    }
    final PsiMethod[] existedConstructors = containingClass.getConstructors();
    setText(QuickFixBundle.message("add.exception.from.field.initializer.to.constructor.throws.text", existedConstructors.length));
    return true;
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final NavigatablePsiElement field =
      PsiTreeUtil.getParentOfType(myWrongElement, PsiMethod.class, PsiFunctionalExpression.class, PsiField.class);
    if (field instanceof PsiField) {
      final PsiClass aClass = ((PsiField)field).getContainingClass();
      if (aClass != null) {
        PsiMethod[] constructors = aClass.getConstructors();
        if (constructors.length == 0) {
          final AddDefaultConstructorFix defaultConstructorFix = new AddDefaultConstructorFix(aClass);
          ApplicationManager.getApplication().runWriteAction(() -> defaultConstructorFix.invoke(project, null, file));
          constructors = aClass.getConstructors();
          LOG.assertTrue(constructors.length != 0);
        }

        Set<PsiClassType> unhandledExceptions = new HashSet<>(ExceptionUtil.getUnhandledExceptions(field));
        for (PsiMethod constructor : constructors) {
          AddExceptionToThrowsFix.addExceptionsToThrowsList(project, constructor, unhandledExceptions);
        }
      }
    }
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("add.exception.from.field.initializer.to.constructor.throws.family.text");
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
