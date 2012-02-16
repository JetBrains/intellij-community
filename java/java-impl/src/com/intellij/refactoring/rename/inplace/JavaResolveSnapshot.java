/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.refactoring.rename.inplace;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;

import java.util.Map;

/**
 * @author ven
 */
class JavaResolveSnapshot extends ResolveSnapshotProvider.ResolveSnapshot {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.inplace.JavaResolveSnapshot");

  private final Map<SmartPsiElementPointer, SmartPsiElementPointer> myReferencesMap = new HashMap<SmartPsiElementPointer, SmartPsiElementPointer>();
  private final Project myProject;
  private final Document myDocument;

  JavaResolveSnapshot(final PsiElement scope) {
    myProject = scope.getProject();
    myDocument = PsiDocumentManager.getInstance(myProject).getDocument(scope.getContainingFile());
    final SmartPointerManager pointerManager = SmartPointerManager.getInstance(myProject);
    final Map<PsiElement, SmartPsiElementPointer> pointers = new HashMap<PsiElement, SmartPsiElementPointer>();
    scope.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression refExpr) {
        if (!refExpr.isQualified()) {
          PsiElement resolved = refExpr.resolve();
          if (resolved instanceof PsiField) {
            SmartPsiElementPointer key = pointerManager.createSmartPsiElementPointer(refExpr);
            SmartPsiElementPointer value = pointers.get(resolved);
            if (value == null) {
              value = pointerManager.createSmartPsiElementPointer(resolved);
              pointers.put(resolved, value);
            }
            myReferencesMap.put(key, value);
          }
        }
        super.visitReferenceExpression(refExpr);
      }
    });
  }

  public void apply(String hidingLocalName) {
    PsiDocumentManager.getInstance(myProject).commitDocument(myDocument);
    for (Map.Entry<SmartPsiElementPointer,SmartPsiElementPointer> entry : myReferencesMap.entrySet()) {
      qualify(entry.getKey().getElement(), entry.getValue().getElement(), hidingLocalName);
    }
  }

  private static void qualify(PsiElement referent, PsiElement referee, String hidingLocalName) {
    if (referent instanceof PsiReferenceExpression && referee instanceof PsiField) {
      PsiReferenceExpression ref = ((PsiReferenceExpression) referent);
      if (!ref.isQualified() && hidingLocalName.equals(ref.getReferenceName())) {
        final PsiElement newlyResolved = ref.resolve();
        if (referee.getManager().areElementsEquivalent(newlyResolved, referee)) return;
        PsiClass refereeClass = ((PsiField) referee).getContainingClass();
        PsiClass referentClass = PsiTreeUtil.getParentOfType(referent, PsiClass.class);
        if (refereeClass != null && referentClass != null &&
            PsiTreeUtil.isAncestor(refereeClass, referentClass, false)) {
          if (refereeClass == referentClass ||
              refereeClass.getName() != null) {  //otherwise cannot qualify anonymous referee class
            @NonNls String qualifer;
            if (RefactoringUtil.isInStaticContext(ref, refereeClass)) {
              qualifer = refereeClass.getName();
            } else {
              qualifer = refereeClass == referentClass ? "this" : refereeClass.getName() + ".this";
            }
            String qualifiedRefText = qualifer + "." + ref.getText();
            PsiElementFactory elementFactory = JavaPsiFacade.getInstance(referentClass.getProject()).getElementFactory();
            try {
              PsiReferenceExpression qualifiedRef = (PsiReferenceExpression) elementFactory.createExpressionFromText(qualifiedRefText, null);
              ref.replace(qualifiedRef);
            } catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }
      }
    }
  }
}
