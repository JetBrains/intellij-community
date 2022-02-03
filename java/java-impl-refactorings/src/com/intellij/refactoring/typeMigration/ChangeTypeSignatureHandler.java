// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.intellij.refactoring.typeMigration;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.typeMigration.ui.TypeMigrationDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ChangeTypeSignatureHandler implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance(ChangeTypeSignatureHandler.class);

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    final int offset = TargetElementUtil.adjustOffset(file, editor.getDocument(), editor.getCaretModel().getOffset());
    final PsiElement element = file.findElementAt(offset);
    PsiTypeElement typeElement = PsiTreeUtil.getParentOfType(element, PsiTypeElement.class);
    while (typeElement != null) {
      final PsiElement parent = typeElement.getParent();
      PsiElement[] toMigrate = null;
      if (parent instanceof PsiVariable) {
        toMigrate = extractReferencedVariables(typeElement);
      }
      else if ((parent instanceof PsiMember && !(parent instanceof PsiClass)) || isClassArgument(parent)) {
        toMigrate = new PsiElement[]{parent};
      }
      if (toMigrate != null && toMigrate.length > 0) {
        invoke(project, toMigrate, editor);
        return;
      }
      typeElement = PsiTreeUtil.getParentOfType(parent, PsiTypeElement.class, false);
    }
    CommonRefactoringUtil.showErrorHint(project, editor,
                                        JavaRefactoringBundle.message("caret.position.warning.message"),
                                        JavaRefactoringBundle.message("type.migration.error.hint.title"), "refactoring.migrateType");
  }

  @Override
  public void invoke(@NotNull final Project project, final PsiElement @NotNull [] elements, final DataContext dataContext) {
    LOG.assertTrue(elements.length == 1);
    final PsiElement element = elements[0];
    invokeOnElement(project, element);
  }

  private static void invokeOnElement(final Project project, final PsiElement element) {
    if (element instanceof PsiVariable ||
        (element instanceof PsiMember && !(element instanceof PsiClass)) ||
        element instanceof PsiFile  ||
        isClassArgument(element)) {
      invoke(project, new PsiElement[] {element}, (Editor)null);
    }
  }

  private static boolean isClassArgument(PsiElement element) {
    if (element instanceof PsiReferenceParameterList) {
      final PsiMember member = PsiTreeUtil.getParentOfType(element, PsiMember.class);
      if (member instanceof PsiAnonymousClass) {
        return ((PsiAnonymousClass)member).getBaseClassReference().getParameterList() == element;
      }
      if (member instanceof PsiClass) {
        final PsiReferenceList implementsList = ((PsiClass)member).getImplementsList();
        final PsiReferenceList extendsList = ((PsiClass)member).getExtendsList();
        return PsiTreeUtil.isAncestor(implementsList, element, false) || 
               PsiTreeUtil.isAncestor(extendsList, element, false);
      }
    }
    return false;
  }

  private static void invoke(@NotNull Project project,
                             PsiElement @NotNull [] roots,
                             @Nullable Editor editor) {
    if (Util.canBeMigrated(roots)) {
      TypeMigrationDialog dialog = new TypeMigrationDialog.SingleElement(project, roots);
      dialog.show();
      return;
    }

    CommonRefactoringUtil.showErrorHint(project, editor, JavaRefactoringBundle.message("only.fields.variables.of.methods.of.valid.type.can.be.considered"),
                                   JavaRefactoringBundle.message("unable.to.start.type.migration"), null);

  }

  private static PsiElement @NotNull [] extractReferencedVariables(@NotNull PsiTypeElement typeElement) {
    final PsiElement parent = typeElement.getParent();
    if (parent instanceof PsiVariable) {
      if (parent instanceof PsiField) {
        PsiField aField = (PsiField)parent;
        List<PsiField> fields = new ArrayList<>();
        while (true) {
          fields.add(aField);
          aField = PsiTreeUtil.getNextSiblingOfType(aField, PsiField.class);
          if (aField == null || aField.getTypeElement() != typeElement) {
            return fields.toArray(PsiElement.EMPTY_ARRAY);
          }
        }
      }
      else if (parent instanceof PsiLocalVariable) {
        final PsiDeclarationStatement declaration = PsiTreeUtil.getParentOfType(parent, PsiDeclarationStatement.class);
        if (declaration != null) {
          return Arrays.stream(declaration.getDeclaredElements()).filter(PsiVariable.class::isInstance).toArray(PsiVariable[]::new);
        }
      }
      return new PsiElement[]{parent};
    } else {
      return PsiElement.EMPTY_ARRAY;
    }
  }
}