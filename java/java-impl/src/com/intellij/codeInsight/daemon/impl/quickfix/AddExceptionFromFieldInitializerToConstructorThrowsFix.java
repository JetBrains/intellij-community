/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public class AddExceptionFromFieldInitializerToConstructorThrowsFix extends BaseIntentionAction {
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
    if ((containingClass == null ||
             containingClass instanceof PsiAnonymousClass ||
             containingClass.isInterface() ||
             !containingClass.isWritable())) {
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

        Set<PsiClassType> unhandledExceptions = new THashSet<>(ExceptionUtil.getUnhandledExceptions(field));
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
