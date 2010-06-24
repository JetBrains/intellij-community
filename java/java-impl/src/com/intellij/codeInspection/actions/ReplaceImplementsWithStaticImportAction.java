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
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ReplaceImplementsWithStaticImportAction extends PsiElementBaseIntentionAction {
  private static final Logger LOG = Logger.getInstance("#" + ReplaceImplementsWithStaticImportAction.class.getName());

  @NotNull
  public String getText() {
    return "Replace Implements with Static Import";
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (!(element.getContainingFile() instanceof PsiJavaFile)) return false;
    if (element instanceof PsiIdentifier) {
      final PsiElement parent = element.getParent();
      if (parent instanceof PsiClass) {
        return isEmptyClass(project, (PsiClass)parent) && DirectClassInheritorsSearch.search((PsiClass)parent).findFirst() != null;
      }
    }
    final PsiReference psiReference = TargetElementUtilBase.findReference(editor);
    if (psiReference == null) return false;

    final PsiReferenceList referenceList = PsiTreeUtil.getParentOfType(psiReference.getElement(), PsiReferenceList.class);
    if (referenceList == null) return false;

    final PsiClass psiClass = PsiTreeUtil.getParentOfType(referenceList, PsiClass.class);
    if (psiClass == null) return false;

    if (psiClass.getExtendsList() != referenceList && psiClass.getImplementsList() != referenceList) return false;

    final PsiElement target = psiReference.resolve();
    if (target == null || !(target instanceof PsiClass)) return false;

    return isEmptyClass(project, (PsiClass)target);
  }

  private static boolean isEmptyClass(Project project, PsiClass targetClass) {
    if (!targetClass.isInterface()) {
      return false;
    }
    final PsiReferenceList extendsList = targetClass.getExtendsList();
    LOG.assertTrue(extendsList != null);
    if (extendsList.getReferencedTypes().length > 0) {
      final List<PsiMethod> methods = new ArrayList<PsiMethod>(Arrays.asList(targetClass.getAllMethods()));
      final PsiClass objectClass =
        JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_LANG_OBJECT, GlobalSearchScope.allScope(project));
      if (objectClass == null) return false;
      methods.removeAll(Arrays.asList(objectClass.getMethods()));
      if (methods.size() > 0) return false;
    }
    else if (targetClass.getMethods().length > 0) {
      return false;
    }
    return targetClass.getAllFields().length > 0;
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtilBase.preparePsiElementForWrite(file)) return;

    final int offset = editor.getCaretModel().getOffset();
    final PsiReference psiReference = file.findReferenceAt(offset);
    if (psiReference != null) {
      final PsiElement element = psiReference.getElement();

      final PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
      LOG.assertTrue(psiClass != null);

      final PsiElement target = psiReference.resolve();
      LOG.assertTrue(target instanceof PsiClass);

      final PsiClass targetClass = (PsiClass)target;

      new WriteCommandAction(project, getText(), file) {
        protected void run(Result result) throws Throwable {
          for (PsiField constField : targetClass.getAllFields()) {
            final PsiClass containingClass = constField.getContainingClass();
            for (PsiReference ref : ReferencesSearch.search(constField, new LocalSearchScope(psiClass))) {
              ((PsiReferenceExpressionImpl)ref)
                .bindToElementViaStaticImport(containingClass, constField.getName(), ((PsiJavaFile)file).getImportList());
            }
          }
          element.delete();
          JavaCodeStyleManager.getInstance(project).optimizeImports(file);
        }
      }.execute();
    }
    else {
      final PsiElement identifier = file.findElementAt(offset);
      LOG.assertTrue(identifier instanceof PsiIdentifier);
      final PsiElement element = identifier.getParent();
      LOG.assertTrue(element instanceof PsiClass);
      final PsiClass targetClass = (PsiClass)element;
      final Map<PsiFile, Map<PsiField, Set<PsiReference>>> refs = new HashMap<PsiFile, Map<PsiField, Set<PsiReference>>>();
      if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
          for (PsiField field : targetClass.getAllFields()) {
            final String fieldName = field.getName();
            if (progressIndicator != null) {
              progressIndicator.setText2(fieldName);
            }
            for (PsiReference reference : ReferencesSearch.search(field)) {
              final PsiFile psiFile = reference.getElement().getContainingFile();
              if (psiFile instanceof PsiJavaFile) {
                Map<PsiField, Set<PsiReference>> references = refs.get(psiFile);
                if (references == null) {
                  references = new HashMap<PsiField, Set<PsiReference>>();
                  refs.put(psiFile, references);
                }
                Set<PsiReference> fieldsRefs = references.get(field);
                if (fieldsRefs == null) {
                  fieldsRefs = new HashSet<PsiReference>();
                  references.put(field, fieldsRefs);
                }
                fieldsRefs.add(reference);
              }
            }
          }
        }
      }, "Find constant field usages...", true, project)) {
        return;
      }

      final Set<PsiJavaCodeReferenceElement> refs2Unimplement = new HashSet<PsiJavaCodeReferenceElement>();
      if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          for (PsiClass psiClass : DirectClassInheritorsSearch.search(targetClass)) {
            PsiFile containingFile = psiClass.getContainingFile();
            if (!refs.containsKey(containingFile)) {
              refs.put(containingFile, new HashMap<PsiField, Set<PsiReference>>());
            }
            if (collectExtendsImplements(targetClass, psiClass.getExtendsList(), refs2Unimplement)) continue;
            collectExtendsImplements(targetClass, psiClass.getImplementsList(), refs2Unimplement);
          }
        }
      }, "Find references in implement/extends lists...", true, project)) {
        return;
      }

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          for (PsiFile psiFile : refs.keySet()) {
            final Map<PsiField, Set<PsiReference>> map = refs.get(psiFile);
            for (PsiField psiField : map.keySet()) {
              final PsiClass containingClass = psiField.getContainingClass();
              final String fieldName = psiField.getName();
              for (PsiReference reference : map.get(psiField)) {
                ((PsiReferenceExpressionImpl)reference)
                .bindToElementViaStaticImport(containingClass, fieldName, ((PsiJavaFile)psiFile).getImportList());
              }
            }
          }

          for (PsiJavaCodeReferenceElement referenceElement : refs2Unimplement) {
            referenceElement.delete();
          }

          for (PsiFile psiFile : refs.keySet()) {
            JavaCodeStyleManager.getInstance(project).optimizeImports(psiFile);
          }
        }
      });
    }
  }

  private static boolean collectExtendsImplements(final PsiClass targetClass,
                                                  final PsiReferenceList referenceList,
                                                  final Set<PsiJavaCodeReferenceElement> refs) {
    if (referenceList != null) {
      for (PsiJavaCodeReferenceElement referenceElement : referenceList.getReferenceElements()) {
        if (referenceElement.resolve() == targetClass) {
          refs.add(referenceElement);
          return true;
        }
      }
    }
    return false;
  }

  public boolean startInWriteAction() {
    return false;
  }
}
