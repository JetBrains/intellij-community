/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInspection.actions;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.actions.OptimizeImportsProcessor;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class ReplaceImplementsWithStaticImportAction implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#" + ReplaceImplementsWithStaticImportAction.class.getName());

  @NotNull
  public String getText() {
    return "Replace Implements with Static Import";
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PsiJavaFile)) return false;
    final PsiReference psiReference = TargetElementUtilBase.findReference(editor);
    if (psiReference == null) return false;

    final PsiReferenceList referenceList = PsiTreeUtil.getParentOfType(psiReference.getElement(), PsiReferenceList.class);
    if (referenceList == null) return false;

    final PsiClass psiClass = PsiTreeUtil.getParentOfType(referenceList, PsiClass.class);
    if (psiClass == null) return false;

    if (psiClass.getExtendsList() != referenceList && psiClass.getImplementsList() != referenceList) return false;

    final PsiElement target = psiReference.resolve();
    if (target == null || !(target instanceof PsiClass)) return false;

    PsiClass targetClass = (PsiClass)target;
    if (!targetClass.isInterface()) {
      return false;
    }

    if (targetClass.getMethods().length > 0) return false;
    
    return targetClass.getFields().length > 0;
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtilBase.preparePsiElementForWrite(file)) return;

    final PsiReference psiReference = file.findReferenceAt(editor.getCaretModel().getOffset());
    LOG.assertTrue(psiReference != null);

    final PsiElement element = psiReference.getElement();

    final PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    LOG.assertTrue(psiClass != null);

    final PsiElement target = psiReference.resolve();
    LOG.assertTrue(target instanceof PsiClass);

    final PsiClass targetClass = (PsiClass)target;

    for (PsiField constField : targetClass.getFields()) {
      for (PsiReference ref : ReferencesSearch.search(constField, new LocalSearchScope(psiClass))) {
        ((PsiReferenceExpressionImpl)ref).bindToElementViaStaticImport(targetClass, constField.getName(), ((PsiJavaFile)file).getImportList());
      }
    }

    element.delete();
    new OptimizeImportsProcessor(project, file).run();
  }

  public boolean startInWriteAction() {
    return true;
  }
}
