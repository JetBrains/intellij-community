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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * @author Danila Ponomarenko
 */
public final class FieldFromParameterUtils {
  @Nullable
  public static PsiParameter findParameterAtCursor(@NotNull PsiFile file, @NotNull Editor editor) {
    final int offset = editor.getCaretModel().getOffset();
    final PsiParameterList parameterList = PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiParameterList.class, false);
    if (parameterList == null) return null;
    final PsiParameter[] parameters = parameterList.getParameters();
    for (PsiParameter parameter : parameters) {
      final TextRange range = parameter.getTextRange();
      if (range.getStartOffset() <= offset && offset <= range.getEndOffset()) return parameter;
    }
    return null;
  }

  @Nullable
  public static PsiType getType(@Nullable PsiParameter myParameter) {
    if (myParameter == null) return null;
    PsiType type = myParameter.getType();
    return type instanceof PsiEllipsisType ? ((PsiEllipsisType)type).toArrayType() : type;
  }

  @Nullable
  public static PsiType getSubstitutedType(@Nullable PsiParameter parameter) {
    if (parameter == null) return null;

    final PsiType type = getType(parameter);

    if (type instanceof PsiArrayType) {
      return type;
    }

    final PsiClassType.ClassResolveResult result = PsiUtil.resolveGenericsClassInType(type);
    final PsiClass psiClass = result.getElement();
    if (psiClass == null) return type;

    final Set<PsiTypeParameter> usedTypeParameters = new HashSet<>();
    RefactoringUtil.collectTypeParameters(usedTypeParameters, parameter);
    for (Iterator<PsiTypeParameter> iterator = usedTypeParameters.iterator(); iterator.hasNext(); ) {
      PsiTypeParameter usedTypeParameter = iterator.next();
      if (parameter.getDeclarationScope() != usedTypeParameter.getOwner()) {
        iterator.remove();
      }
    }

    PsiSubstitutor subst = PsiSubstitutor.EMPTY;
    for (PsiTypeParameter usedTypeParameter : usedTypeParameters) {
      final PsiType bound = TypeConversionUtil.typeParameterErasure(usedTypeParameter);
      final PsiManager manager = usedTypeParameter.getManager();
      subst = subst.put(usedTypeParameter, bound == null ? PsiWildcardType.createUnbounded(manager) : bound.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) ? bound : PsiWildcardType.createExtends(manager, bound));
    }

    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    final Map<PsiTypeParameter, PsiType> typeMap = result.getSubstitutor().getSubstitutionMap();
    for (PsiTypeParameter typeParameter : typeMap.keySet()) {
      final PsiType psiType = typeMap.get(typeParameter);
      substitutor = substitutor.put(typeParameter, psiType != null ? subst.substitute(psiType) : null);
    }

    if (psiClass instanceof PsiTypeParameter) {
      return GenericsUtil.getVariableTypeByExpressionType(subst.substitute((PsiTypeParameter)psiClass));
    }
    else {
      return JavaPsiFacade.getElementFactory(parameter.getProject()).createType(psiClass, substitutor);
    }
  }

  @Nullable
  public static PsiField getParameterAssignedToField(final PsiParameter parameter) {
    for (PsiReference reference : ReferencesSearch.search(parameter, new LocalSearchScope(parameter.getDeclarationScope()), false)) {
      if (!(reference instanceof PsiReferenceExpression)) continue;
      final PsiReferenceExpression expression = (PsiReferenceExpression)reference;
      if (!(expression.getParent() instanceof PsiAssignmentExpression)) continue;
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression.getParent();
      if (assignmentExpression.getRExpression() != expression) continue;
      final PsiExpression lExpression = assignmentExpression.getLExpression();
      if (!(lExpression instanceof PsiReferenceExpression)) continue;
      final PsiElement element = ((PsiReferenceExpression)lExpression).resolve();
      if (element instanceof PsiField) return (PsiField)element;
    }
    return null;
  }

  public static int findFieldAssignmentAnchor(final PsiStatement[] statements,
                                              final @Nullable Ref<Pair<PsiField, Boolean>> anchorRef,
                                              final PsiClass targetClass,
                                              final PsiParameter myParameter) {
    int i = 0;
    for (; i < statements.length; i++) {
      PsiStatement psiStatement = statements[i];

      if (psiStatement instanceof PsiExpressionStatement) {
        PsiExpressionStatement expressionStatement = (PsiExpressionStatement)psiStatement;
        PsiExpression expression = expressionStatement.getExpression();

        if (expression instanceof PsiMethodCallExpression) {
          PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
          String text = methodCallExpression.getMethodExpression().getText();
          if (text.equals("super") || text.equals("this")) {
            continue;
          }
        }
        else if (expression instanceof PsiAssignmentExpression) {
          PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression;
          PsiExpression lExpression = assignmentExpression.getLExpression();

          if (!(lExpression instanceof PsiReferenceExpression)) break;

          PsiElement lElement = ((PsiReferenceExpression)lExpression).resolve();
          if (!(lElement instanceof PsiField) || ((PsiField)lElement).getContainingClass() != targetClass) break;

          final Set<PsiParameter> parameters = new HashSet<>();
          SyntaxTraverser.psiTraverser().withRoot(assignmentExpression.getRExpression())
            .filter(PsiReferenceExpression.class)
            .forEach(expr -> {
              final PsiElement resolve = expr.resolve();
              if (resolve instanceof PsiParameter && ((PsiParameter)resolve).getDeclarationScope() == myParameter.getDeclarationScope()) {
                parameters.add((PsiParameter)resolve);
              }
            });

          if (parameters.size() != 1) break;

          PsiElement rElement = parameters.iterator().next();

          if (myParameter.getTextRange().getStartOffset() < rElement.getTextRange().getStartOffset()) {
            if (anchorRef != null) {
              anchorRef.set(Pair.create((PsiField)lElement, Boolean.TRUE));
            }
            break;
          }

          if (anchorRef != null) {
            anchorRef.set(Pair.create((PsiField)lElement, Boolean.FALSE));
          }
          continue;
        }
      }

      break;
    }
    return i;
  }

  public static void createFieldAndAddAssignment(final @NotNull Project project,
                                                 final @NotNull PsiClass targetClass,
                                                 final @NotNull PsiMethod method,
                                                 final @NotNull PsiParameter parameter,
                                                 final @NotNull PsiType fieldType,
                                                 final @NotNull String fieldName,
                                                 final boolean isStatic,
                                                 final boolean isFinal) throws IncorrectOperationException {
    PsiManager psiManager = PsiManager.getInstance(project);
    PsiElementFactory factory = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory();

    PsiField field = factory.createField(fieldName, fieldType);

    PsiModifierList modifierList = field.getModifierList();
    if (modifierList == null) return;
    modifierList.setModifierProperty(PsiModifier.STATIC, isStatic);
    modifierList.setModifierProperty(PsiModifier.FINAL, isFinal);

    NullableNotNullManager manager = NullableNotNullManager.getInstance(project);
    if (manager.copyNullableAnnotation(parameter, field) == null && isFinal) {
      manager.copyNotNullAnnotation(parameter, field);
    }

    PsiCodeBlock methodBody = method.getBody();
    if (methodBody == null) return;
    PsiStatement[] statements = methodBody.getStatements();

    Ref<Pair<PsiField, Boolean>> anchorRef = new Ref<>();
    int i = findFieldAssignmentAnchor(statements, anchorRef, targetClass, parameter);
    Pair<PsiField, Boolean> fieldAnchor = anchorRef.get();

    String stmtText = fieldName + " = " + parameter.getName() + ";";
    if (fieldName.equals(parameter.getName())) {
      String prefix = isStatic ? targetClass.getName() == null ? "" : targetClass.getName() + "." : "this.";
      stmtText = prefix + stmtText;
    }

    PsiStatement assignmentStmt = factory.createStatementFromText(stmtText, methodBody);
    assignmentStmt = (PsiStatement)CodeStyleManager.getInstance(project).reformat(assignmentStmt);

    if (i == statements.length) {
      methodBody.add(assignmentStmt);
    }
    else {
      methodBody.addAfter(assignmentStmt, i > 0 ? statements[i - 1] : null);
    }

    if (fieldAnchor != null) {
      PsiVariable psiVariable = fieldAnchor.getFirst();
      psiVariable.normalizeDeclaration();
    }

    if (targetClass.findFieldByName(fieldName, false) == null) {
      if (fieldAnchor != null) {
        Boolean insertBefore = fieldAnchor.getSecond();
        PsiField inField = fieldAnchor.getFirst();
        if (insertBefore.booleanValue()) {
          targetClass.addBefore(field, inField);
        }
        else {
          targetClass.addAfter(field, inField);
        }
      }
      else {
        targetClass.add(field);
      }
    }
  }

  public static boolean isAvailable(@Nullable PsiParameter myParameter, @Nullable PsiType type, @Nullable PsiClass targetClass) {
    return myParameter != null
           && myParameter.isValid()
           && myParameter.getManager().isInProject(myParameter)
           && myParameter.getDeclarationScope() instanceof PsiMethod
           && ((PsiMethod)myParameter.getDeclarationScope()).getBody() != null
           && type != null
           && type.isValid()
           && targetClass != null
           && !targetClass.isInterface()
           && getParameterAssignedToField(myParameter) == null;
  }

  private FieldFromParameterUtils() { }
}
