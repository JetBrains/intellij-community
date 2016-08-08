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
package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.codeInsight.lookup.VariableLookupItem;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * @author peter
 */
public class JavaMembersGetter extends MembersGetter {
  private final PsiType myExpectedType;

  public JavaMembersGetter(@NotNull PsiType expectedType, CompletionParameters parameters) {
    super(new JavaStaticMemberProcessor(parameters), parameters.getPosition());
    myExpectedType = JavaCompletionUtil.originalize(expectedType);
  }

  public void addMembers(boolean searchInheritors, final Consumer<LookupElement> results) {
    addConstantsFromTargetClass(results, searchInheritors);
    if (myExpectedType instanceof PsiPrimitiveType && PsiType.DOUBLE.isAssignableFrom(myExpectedType)) {
      addConstantsFromReferencedClassesInSwitch(results);
    }

    if (myPlace.getParent().getParent() instanceof PsiSwitchLabelStatement) {
      return; //non-enum values are processed above, enum values will be suggested by reference completion
    }

    final PsiClass psiClass = PsiUtil.resolveClassInType(myExpectedType);
    processMembers(results, psiClass, PsiTreeUtil.getParentOfType(myPlace, PsiAnnotation.class) == null, searchInheritors);
  }

  private void addConstantsFromReferencedClassesInSwitch(final Consumer<LookupElement> results) {
    final Set<PsiField> fields = ReferenceExpressionCompletionContributor.findConstantsUsedInSwitch(myPlace);
    final Set<PsiClass> classes = new HashSet<>();
    for (PsiField field : fields) {
      ContainerUtil.addIfNotNull(classes, field.getContainingClass());
    }
    for (PsiClass aClass : classes) {
      processMembers(element -> {
        //noinspection SuspiciousMethodCalls
        if (!fields.contains(element.getObject())) {
          results.consume(TailTypeDecorator.withTail(element, TailType.CASE_COLON));
        }
      }, aClass, true, false);
    }
  }

  private void addConstantsFromTargetClass(Consumer<LookupElement> results, boolean searchInheritors) {
    PsiElement parent = myPlace.getParent();
    if (!(parent instanceof PsiReferenceExpression)) {
      return;
    }

    PsiElement prev = parent;
    parent = parent.getParent();
    while (parent instanceof PsiBinaryExpression) {
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)parent;
      final IElementType op = binaryExpression.getOperationTokenType();
      if (JavaTokenType.EQEQ == op || JavaTokenType.NE == op) {
        if (prev == binaryExpression.getROperand()) {
          processMembers(results, getCalledClass(binaryExpression.getLOperand()), true, searchInheritors
          );
        }
        return;
      }
      prev = parent;
      parent = parent.getParent();
    }
    if (parent instanceof PsiExpressionList) {
      processMembers(results, getCalledClass(parent.getParent()), true, searchInheritors);
    }
  }

  @Nullable
  private static PsiClass getCalledClass(@Nullable PsiElement call) {
    if (call instanceof PsiMethodCallExpression) {
      for (final JavaResolveResult result : ((PsiMethodCallExpression)call).getMethodExpression().multiResolve(true)) {
        final PsiElement element = result.getElement();
        if (element instanceof PsiMethod) {
          final PsiClass aClass = ((PsiMethod)element).getContainingClass();
          if (aClass != null && !"java.lang.Math".equals(aClass.getQualifiedName())) {
            return aClass;
          }
        }
      }
    }
    if (call instanceof PsiNewExpression) {
      final PsiJavaCodeReferenceElement reference = ((PsiNewExpression)call).getClassReference();
      if (reference != null) {
        for (final JavaResolveResult result : reference.multiResolve(true)) {
          final PsiElement element = result.getElement();
          if (element instanceof PsiClass) {
            return (PsiClass)element;
          }
        }
      }
    }
    return null;
  }

  @Override
  @Nullable
  protected LookupElement createFieldElement(PsiField field) {
    if (!myExpectedType.isAssignableFrom(field.getType())) {
      return null;
    }

    return new VariableLookupItem(field, false);
  }

  @Override
  @Nullable
  protected LookupElement createMethodElement(PsiMethod method) {
    PsiSubstitutor substitutor = SmartCompletionDecorator.calculateMethodReturnTypeSubstitutor(method, myExpectedType);
    PsiType type = substitutor.substitute(method.getReturnType());
    if (type == null || !myExpectedType.isAssignableFrom(type)) {
      return null;
    }


    JavaMethodCallElement item = new JavaMethodCallElement(method, false, false);
    item.setInferenceSubstitutor(substitutor, myPlace);
    return item;
  }
}
