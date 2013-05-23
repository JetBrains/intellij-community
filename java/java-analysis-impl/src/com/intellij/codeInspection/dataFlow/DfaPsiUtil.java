/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DfaPsiUtil {
  public static boolean isPlainMutableField(PsiVariable var) {
    return !var.hasModifierProperty(PsiModifier.FINAL) && !var.hasModifierProperty(PsiModifier.TRANSIENT) && !var.hasModifierProperty(PsiModifier.VOLATILE) && var instanceof PsiField;
  }

  public static boolean isFinalField(PsiVariable var) {
    return var.hasModifierProperty(PsiModifier.FINAL) && !var.hasModifierProperty(PsiModifier.TRANSIENT) && var instanceof PsiField;
  }

  static PsiElement getEnclosingCodeBlock(final PsiVariable variable, final PsiElement context) {
    PsiElement codeBlock;
    if (variable instanceof PsiParameter) {
      codeBlock = ((PsiParameter)variable).getDeclarationScope();
      if (codeBlock instanceof PsiMethod) {
        codeBlock = ((PsiMethod)codeBlock).getBody();
      }
    }
    else if (variable instanceof PsiLocalVariable) {
      codeBlock = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
    }
    else {
      codeBlock = PsiTreeUtil.getParentOfType(context, PsiCodeBlock.class);
    }
    while (codeBlock != null) {
      PsiAnonymousClass anon = PsiTreeUtil.getParentOfType(codeBlock, PsiAnonymousClass.class);
      if (anon == null) break;
      codeBlock = PsiTreeUtil.getParentOfType(anon, PsiCodeBlock.class);
    }
    return codeBlock;
  }

  @NotNull
  public static Nullness getElementNullability(@Nullable PsiType resultType, @Nullable PsiModifierListOwner owner) {
    if (owner == null) {
      return Nullness.UNKNOWN;
    }

    if (NullableNotNullManager.isNullable(owner)) {
      return Nullness.NULLABLE;
    }
    if (NullableNotNullManager.isNotNull(owner)) {
      return Nullness.NOT_NULL;
    }

    if (resultType != null) {
      NullableNotNullManager nnn = NullableNotNullManager.getInstance(owner.getProject());
      for (PsiAnnotation annotation : resultType.getAnnotations()) {
        String qualifiedName = annotation.getQualifiedName();
        if (nnn.getNullables().contains(qualifiedName)) {
          return Nullness.NULLABLE;
        }
        if (nnn.getNotNulls().contains(qualifiedName)) {
          return Nullness.NOT_NULL;
        }
      }
    }

    return Nullness.UNKNOWN;
  }

  public static List<PsiExpression> findAllConstructorInitializers(PsiField field) {
    final List<PsiExpression> result = ContainerUtil.createLockFreeCopyOnWriteList();
    ContainerUtil.addIfNotNull(result, field.getInitializer());

    PsiClass containingClass = field.getContainingClass();
    if (containingClass != null) {
      LocalSearchScope scope = new LocalSearchScope(containingClass.getConstructors());
      ReferencesSearch.search(field, scope, false).forEach(new Processor<PsiReference>() {
        @Override
        public boolean process(PsiReference reference) {
          final PsiElement element = reference.getElement();
          if (element instanceof PsiReferenceExpression) {
            final PsiAssignmentExpression assignment = getAssignmentExpressionIfOnAssignmentLhs(element);
            final PsiMethod method = PsiTreeUtil.getParentOfType(assignment, PsiMethod.class);
            if (method != null && method.isConstructor() && assignment != null) {
              ContainerUtil.addIfNotNull(result, assignment.getRExpression());
            }
          }
          return true;
        }
      });
    }
    return result;
  }

  @Nullable
  private static PsiAssignmentExpression getAssignmentExpressionIfOnAssignmentLhs(PsiElement expression) {
    PsiElement parent = PsiTreeUtil.skipParentsOfType(expression, PsiParenthesizedExpression.class);
    if (!(parent instanceof PsiAssignmentExpression)) {
      return null;
    }
    final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
    if (!PsiTreeUtil.isAncestor(assignmentExpression.getLExpression(), expression, false)) {
      return null;
    }
    return assignmentExpression;
  }

  public static boolean isNullableInitialized(PsiVariable var, boolean nullable) {
    if (!isFinalField(var)) {
      return false;
    }

    List<PsiExpression> initializers = findAllConstructorInitializers((PsiField)var);
    if (initializers.isEmpty()) {
      return false;
    }

    for (PsiExpression expression : initializers) {
      if (!(expression instanceof PsiReferenceExpression)) {
        return false;
      }
      PsiElement target = ((PsiReferenceExpression)expression).resolve();
      if (!(target instanceof PsiParameter)) {
        return false;
      }
      if (nullable && NullableNotNullManager.isNullable((PsiParameter)target)) {
        return true;
      }
      if (!nullable && !NullableNotNullManager.isNotNull((PsiParameter)target)) {
        return false;
      }
    }
    return !nullable;
  }

  @Nullable
  public static PsiCodeBlock getTopmostBlockInSameClass(@NotNull PsiElement position) {
    PsiCodeBlock block = PsiTreeUtil.getParentOfType(position, PsiCodeBlock.class, false, PsiMember.class, PsiFile.class);
    if (block == null) {
      return null;
    }

    PsiCodeBlock lastBlock = block;
    while (true) {
      block = PsiTreeUtil.getParentOfType(block, PsiCodeBlock.class, true, PsiMember.class, PsiFile.class);
      if (block == null) {
        return lastBlock;
      }
      lastBlock = block;
    }
  }

  @NotNull
  public static Collection<PsiExpression> getVariableAssignmentsInFile(@NotNull PsiVariable psiVariable,
                                                                       final boolean literalsOnly,
                                                                       final PsiElement place) {
    final Ref<Boolean> modificationRef = Ref.create(Boolean.FALSE);
    final PsiCodeBlock codeBlock = place == null? null : getTopmostBlockInSameClass(place);
    final int placeOffset = codeBlock != null? place.getTextRange().getStartOffset() : 0;
    List<PsiExpression> list = ContainerUtil.mapNotNull(
      ReferencesSearch.search(psiVariable, new LocalSearchScope(new PsiElement[] {psiVariable.getContainingFile()}, null, true)).findAll(),
      new NullableFunction<PsiReference, PsiExpression>() {
        @Override
        public PsiExpression fun(final PsiReference psiReference) {
          if (modificationRef.get()) return null;
          final PsiElement parent = psiReference.getElement().getParent();
          if (parent instanceof PsiAssignmentExpression) {
            final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
            final IElementType operation = assignmentExpression.getOperationTokenType();
            if (assignmentExpression.getLExpression() == psiReference) {
              if (JavaTokenType.EQ.equals(operation)) {
                final PsiExpression rValue = assignmentExpression.getRExpression();
                if (!literalsOnly || allOperandsAreLiterals(rValue)) {
                  // if there's a codeBlock omit the values assigned later
                  if (codeBlock != null && PsiTreeUtil.isAncestor(codeBlock, parent, true)
                      && placeOffset < parent.getTextRange().getStartOffset()) {
                    return null;
                  }
                  return rValue;
                }
                else {
                  modificationRef.set(Boolean.TRUE);
                }
              }
              else if (JavaTokenType.PLUSEQ.equals(operation)) {
                modificationRef.set(Boolean.TRUE);
              }
            }
          }
          return null;
        }
      });
    if (modificationRef.get()) return Collections.emptyList();
    PsiExpression initializer = psiVariable.getInitializer();
    if (initializer != null && (!literalsOnly || allOperandsAreLiterals(initializer))) {
      list = ContainerUtil.concat(list, Collections.singletonList(initializer));
    }
    return list;
  }

  public static boolean allOperandsAreLiterals(@Nullable final PsiExpression expression) {
    if (expression == null) return false;
    if (expression instanceof PsiLiteralExpression) return true;
    if (expression instanceof PsiPolyadicExpression) {
      Stack<PsiExpression> stack = new Stack<PsiExpression>();
      stack.add(expression);
      while (!stack.isEmpty()) {
        PsiExpression psiExpression = stack.pop();
        if (psiExpression instanceof PsiPolyadicExpression) {
          PsiPolyadicExpression binaryExpression = (PsiPolyadicExpression)psiExpression;
          for (PsiExpression op : binaryExpression.getOperands()) {
            stack.push(op);
          }
        }
        else if (!(psiExpression instanceof PsiLiteralExpression)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }
}
