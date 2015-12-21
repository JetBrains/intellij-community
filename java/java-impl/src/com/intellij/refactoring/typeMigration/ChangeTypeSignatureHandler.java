/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.refactoring.typeMigration;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.typeMigration.ui.TypeMigrationDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

public class ChangeTypeSignatureHandler implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance("#" + ChangeTypeSignatureHandler.class.getName());

  public static final String REFACTORING_NAME = "Type Migration";

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    final int offset = TargetElementUtil.adjustOffset(file, editor.getDocument(), editor.getCaretModel().getOffset());
    final PsiElement element = file.findElementAt(offset);
    PsiTypeElement typeElement = PsiTreeUtil.getParentOfType(element, PsiTypeElement.class);
    while (typeElement != null) {
      final PsiElement parent = typeElement.getParent();
      if (parent instanceof PsiVariable || (parent instanceof PsiMember && !(parent instanceof PsiClass)) || isClassArgument(parent)) {
        invoke(project, parent, null, null, editor);
        return;
      }
      typeElement = PsiTreeUtil.getParentOfType(parent, PsiTypeElement.class, false);
    }
    CommonRefactoringUtil.showErrorHint(project, editor,
                                        "The caret should be positioned on type of field, variable, method or method parameter to be refactored",
                                        REFACTORING_NAME, "refactoring.migrateType");
  }


  public void invoke(@NotNull final Project project, @NotNull final PsiElement[] elements, final DataContext dataContext) {
    LOG.assertTrue(elements.length == 1);
    final PsiElement element = elements[0];
    invokeOnElement(project, element);
  }

  public static boolean invokeOnElement(final Project project, final PsiElement element) {
    if (element instanceof PsiVariable || (element instanceof PsiMember && !(element instanceof PsiClass)) || element instanceof PsiFile) {
      invoke(project, element, null, null, null);
      return true;
    }
    if (isClassArgument(element)) {
      invoke(project, element, null, null, null);
      return true;
    }
    return false;
  }

  protected static boolean isClassArgument(PsiElement element) {
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

  public static void invoke(final Project project, final PsiElement root, final PsiType type, final TypeMigrationRules rules, final Editor editor) {
    if (Util.canBeMigrated(root)) {
      TypeMigrationDialog dialog = new TypeMigrationDialog.SingleElement(project, root, type, rules);
      dialog.show();
      return;
    }

    CommonRefactoringUtil.showErrorHint(project, editor, RefactoringBundle.message("only.fields.variables.of.methods.of.valid.type.can.be.considered"),
                                   RefactoringBundle.message("unable.to.start.type.migration"), null);

  }
}