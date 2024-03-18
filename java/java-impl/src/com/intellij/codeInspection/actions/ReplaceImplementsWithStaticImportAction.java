/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.javadoc.PsiDocMethodOrFieldRef;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class ReplaceImplementsWithStaticImportAction implements ModCommandAction {
  @Override
  @NotNull
  public String getFamilyName() {
    return JavaBundle.message("intention.text.replace.implements.with.static.import");
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    PsiFile file = context.file();
    if (!(file instanceof PsiJavaFile)) return null;

    if (getTargetClass(context.findLeaf()) == null) return null;
    return Presentation.of(getFamilyName());
  }

  private static boolean isEmptyInterfaceWithFields(@NotNull PsiClass targetClass) {
    if (!targetClass.isInterface()) {
      return false;
    }
    final PsiReferenceList extendsList = targetClass.getExtendsList();
    if (extendsList != null && extendsList.getReferencedTypes().length > 0) {
      final List<PsiMethod> methods = new ArrayList<>(Arrays.asList(targetClass.getAllMethods()));
      final PsiClass objectClass =
        JavaPsiFacade.getInstance(targetClass.getProject()).findClass(CommonClassNames.JAVA_LANG_OBJECT, targetClass.getResolveScope());
      if (objectClass == null) return false;
      methods.removeAll(Arrays.asList(objectClass.getMethods()));
      if (!methods.isEmpty()) return false;
    }
    else if (targetClass.getMethods().length > 0) {
      return false;
    }
    return targetClass.getAllFields().length > 0;
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    PsiElement leaf = context.findLeaf();
    final PsiClass targetClass = Objects.requireNonNull(getTargetClass(leaf));
    return ModCommand.psiUpdate(context, updater -> {
      final Map<PsiJavaFile, Map<PsiField, Set<PsiElement>>> refs = new HashMap<>();
      for (PsiField field : targetClass.getAllFields()) {
        final PsiClass containingClass = field.getContainingClass();
        for (PsiReference reference : ReferencesSearch.search(field)) {
          if (reference == null) continue;
          final PsiElement refElement = updater.getWritable(reference.getElement());
          if (encodeQualifier(containingClass, refElement, targetClass)) continue;
          PsiFile psiFile = refElement.getContainingFile();
          if (psiFile instanceof PsiJavaFile javaFile) {
            Map<PsiField, Set<PsiElement>> references = refs.computeIfAbsent(javaFile, k -> new HashMap<>());
            Set<PsiElement> fieldsRefs = references.computeIfAbsent(field, k -> new HashSet<>());
            fieldsRefs.add(refElement);
          }
        }
      }

      final Set<PsiJavaCodeReferenceElement> refs2Unimplement = new HashSet<>();
      for (PsiClass psiClass : DirectClassInheritorsSearch.search(targetClass)) {
        PsiFile containingFile = psiClass.getContainingFile();
        if (!(containingFile instanceof PsiJavaFile javaFile)) continue;
        refs.computeIfAbsent(updater.getWritable(javaFile), k -> new HashMap<>());
        if (collectExtendsImplements(targetClass, psiClass.getExtendsList(), refs2Unimplement)) continue;
        collectExtendsImplements(targetClass, psiClass.getImplementsList(), refs2Unimplement);
      }

      List<PsiJavaCodeReferenceElement> writableRefsToUnimplement = ContainerUtil.map(refs2Unimplement, updater::getWritable);
      
      for (PsiJavaFile psiFile : refs.keySet()) {
        final Map<PsiField, Set<PsiElement>> map = refs.get(psiFile);
        for (PsiField psiField : map.keySet()) {
          final PsiClass containingClass = psiField.getContainingClass();
          final String fieldName = psiField.getName();
          for (PsiElement reference : map.get(psiField)) {
            bindReference(psiFile, psiField, containingClass, fieldName, reference, context.project());
          }
        }
      }

      for (PsiJavaCodeReferenceElement referenceElement : writableRefsToUnimplement) {
        referenceElement.delete();
      }

      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(context.project());
      for (PsiFile psiFile : refs.keySet()) {
        if (psiFile instanceof PsiJavaFile javaFile) {
          PsiImportList oldImports = javaFile.getImportList();
          final PsiImportList prepared = codeStyleManager.prepareOptimizeImportsResult(javaFile);
          if (oldImports != null && prepared != null) {
            oldImports.replace(prepared);
          }
        }
      }
    });
  }

  @Nullable
  private static PsiClass getTargetClass(PsiElement element) {
    if (element instanceof PsiIdentifier) {
      final PsiElement parent = element.getParent();
      if (parent instanceof PsiClass psiClass) {
        return isEmptyInterfaceWithFields(psiClass) && DirectClassInheritorsSearch.search(psiClass).findFirst() != null ? psiClass : null;
      }
    }
    PsiJavaCodeReferenceElement ref = PsiTreeUtil.getNonStrictParentOfType(element, PsiJavaCodeReferenceElement.class);
    if (ref == null) return null;

    final PsiReferenceList referenceList = PsiTreeUtil.getParentOfType(ref, PsiReferenceList.class);
    if (referenceList == null) return null;

    final PsiClass psiClass = PsiTreeUtil.getParentOfType(referenceList, PsiClass.class);
    if (psiClass == null) return null;

    if (psiClass.getExtendsList() != referenceList && psiClass.getImplementsList() != referenceList) return null;

    final PsiElement target = ref.resolve();
    if (!(target instanceof PsiClass targetClass)) return null;

    return isEmptyInterfaceWithFields(targetClass) ? targetClass : null;
  }

  private static boolean encodeQualifier(PsiClass containingClass, PsiElement reference, PsiClass targetClass) {
    if (reference instanceof PsiReferenceExpression ref) {
      final PsiElement qualifier = ref.getQualifier();
      if (qualifier != null) {
        if (qualifier instanceof PsiReferenceExpression qualifierRef) {
          final PsiElement resolved = qualifierRef.resolve();
          if (resolved == containingClass || 
              resolved instanceof PsiClass resolvedClass && InheritanceUtil.isInheritorOrSelf(targetClass, resolvedClass, true)) {
            return true;
          }
        }
        qualifier.putCopyableUserData(ChangeContextUtil.CAN_REMOVE_QUALIFIER_KEY,
                                      ChangeContextUtil.canRemoveQualifier(ref));
      }
    }
    return false;
  }

  private static void bindReference(PsiJavaFile psiFile,
                                    PsiField psiField,
                                    PsiClass containingClass,
                                    String fieldName,
                                    PsiElement reference,
                                    Project project) {
    if (reference instanceof PsiReferenceExpression ref) {
      PsiImportList importList = psiFile.getImportList();
      if (importList != null) {
        PsiReferenceExpressionImpl.bindToElementViaStaticImport(containingClass, fieldName, importList);
        final PsiElement qualifier = ref.getQualifier();
        if (qualifier != null) {
          final Boolean canRemoveQualifier = qualifier.getCopyableUserData(ChangeContextUtil.CAN_REMOVE_QUALIFIER_KEY);
          if (canRemoveQualifier != null && canRemoveQualifier.booleanValue()) {
            qualifier.delete();
          } else {
            final PsiJavaCodeReferenceElement classReferenceElement =
              JavaPsiFacade.getElementFactory(project).createReferenceExpression(containingClass);
            qualifier.replace(classReferenceElement);
          }
        }
      }
    } else if (reference instanceof PsiDocMethodOrFieldRef){
      Objects.requireNonNull(reference.getReference()).bindToElement(psiField);    //todo refs through inheritors
    }
  }

  private static boolean collectExtendsImplements(final PsiClass targetClass,
                                                  final PsiReferenceList referenceList,
                                                  final Set<? super PsiJavaCodeReferenceElement> refs) {
    if (referenceList != null) {
      for (PsiJavaCodeReferenceElement referenceElement : referenceList.getReferenceElements()) {
        if (referenceElement.isReferenceTo(targetClass)) {
          refs.add(referenceElement);
          return true;
        }
      }
    }
    return false;
  }
}
