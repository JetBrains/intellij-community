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
import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Danila Ponomarenko
 */
public final class FieldFromParameterUtils {
  @Nullable
  public static PsiParameter findParameterAtCursor(@NotNull PsiFile file, @NotNull Editor editor) {
    int offset = editor.getCaretModel().getOffset();
    PsiParameterList parameterList = PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiParameterList.class, false);
    if (parameterList == null) return null;
    PsiParameter[] parameters = parameterList.getParameters();
    for (PsiParameter parameter : parameters) {
      TextRange range = parameter.getTextRange();
      if (range.getStartOffset() <= offset && offset <= range.getEndOffset()) return parameter;
    }
    return null;
  }

  @NotNull
  public static PsiType getType(@NotNull PsiParameter myParameter) {
    PsiType type = myParameter.getType();
    return type instanceof PsiEllipsisType ? ((PsiEllipsisType)type).toArrayType() : type;
  }

  @Nullable
  public static PsiType getSubstitutedType(@NotNull PsiParameter parameter) {
    PsiType type = getType(parameter);

    if (type instanceof PsiArrayType) {
      return type;
    }

    PsiClassType.ClassResolveResult result = PsiUtil.resolveGenericsClassInType(type);
    PsiClass psiClass = result.getElement();
    if (psiClass == null) {
      return type;
    }

    Set<PsiTypeParameter> usedTypeParameters = new HashSet<>();
    CommonJavaRefactoringUtil.collectTypeParameters(usedTypeParameters, parameter);
    usedTypeParameters.removeIf(usedTypeParameter -> parameter.getDeclarationScope() != usedTypeParameter.getOwner());

    PsiSubstitutor subst = PsiSubstitutor.EMPTY;
    for (PsiTypeParameter usedTypeParameter : usedTypeParameters) {
      PsiType bound = TypeConversionUtil.typeParameterErasure(usedTypeParameter);
      PsiManager manager = usedTypeParameter.getManager();
      subst = subst.put(usedTypeParameter, bound == null ? PsiWildcardType.createUnbounded(manager) : bound.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) ? bound : PsiWildcardType.createExtends(manager, bound));
    }

    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    Map<PsiTypeParameter, PsiType> typeMap = result.getSubstitutor().getSubstitutionMap();
    for (PsiTypeParameter typeParameter : typeMap.keySet()) {
      PsiType psiType = typeMap.get(typeParameter);
      substitutor = substitutor.put(typeParameter, psiType != null ? subst.substitute(psiType) : null);
    }

    if (psiClass instanceof PsiTypeParameter) {
      return GenericsUtil.getVariableTypeByExpressionType(subst.substitute((PsiTypeParameter)psiClass));
    }
    return JavaPsiFacade.getElementFactory(parameter.getProject()).createType(psiClass, substitutor);
  }

  @Nullable
  public static PsiField getParameterAssignedToField(@NotNull PsiParameter parameter) {
    return getParameterAssignedToField(parameter, true);
  }

  @Nullable
  public static PsiField getParameterAssignedToField(@NotNull PsiParameter parameter, boolean findIndirectAssignments) {
    for (PsiReference reference : ReferencesSearch.search(parameter, new LocalSearchScope(parameter.getDeclarationScope()), false)) {
      if (!(reference instanceof PsiReferenceExpression)) continue;
      PsiReferenceExpression expression = (PsiReferenceExpression)reference;
      PsiAssignmentExpression assignmentExpression;
      if (findIndirectAssignments) {
        assignmentExpression = PsiTreeUtil.getParentOfType(expression, PsiAssignmentExpression.class, true, PsiClass.class);
      }
      else {
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
        if (parent instanceof PsiExpressionList) {
          // skip validating calls like Objects.requireNonNull
          PsiMethodCallExpression call = ObjectUtils.tryCast(parent.getParent(), PsiMethodCallExpression.class);
          PsiExpression returnedValue = PsiUtil.skipParenthesizedExprDown(JavaMethodContractUtil.findReturnedValue(call));
          if (returnedValue == expression) {
            parent = PsiUtil.skipParenthesizedExprUp(call.getParent());
          }
        }
        assignmentExpression = parent instanceof PsiAssignmentExpression ? (PsiAssignmentExpression)parent : null;
      }
      if (assignmentExpression == null) continue;
      if (!PsiTreeUtil.isAncestor(assignmentExpression.getRExpression(), expression, false)) continue;
      PsiExpression lExpression = assignmentExpression.getLExpression();
      if (!(lExpression instanceof PsiReferenceExpression)) continue;
      PsiElement element = ((PsiReferenceExpression)lExpression).resolve();
      if (element instanceof PsiField) return (PsiField)element;
    }
    return null;
  }

  public static int findFieldAssignmentAnchor(PsiStatement @NotNull [] statements,
                                              @Nullable Ref<? super PsiField> outAnchor,
                                              @Nullable AtomicBoolean outBefore,
                                              @NotNull PsiClass targetClass,
                                              @NotNull PsiParameter myParameter) {
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

          Set<PsiParameter> parameters = new HashSet<>();
          SyntaxTraverser.psiTraverser().withRoot(assignmentExpression.getRExpression())
            .filter(PsiReferenceExpression.class)
            .forEach(expr -> {
              PsiElement resolve = expr.resolve();
              if (resolve instanceof PsiParameter && ((PsiParameter)resolve).getDeclarationScope() == myParameter.getDeclarationScope()) {
                parameters.add((PsiParameter)resolve);
              }
            });

          if (parameters.size() != 1) break;

          PsiElement rElement = parameters.iterator().next();

          if (myParameter.getTextRange().getStartOffset() < rElement.getTextRange().getStartOffset()) {
            if (outAnchor != null) {
              outAnchor.set((PsiField)lElement);
            }
            if (outBefore != null) {
              outBefore.set(true);
            }
            break;
          }

          if (outAnchor != null) {
            outAnchor.set((PsiField)lElement);
          }
          if (outBefore != null) {
            outBefore.set(false);
          }
          continue;
        }
      }

      break;
    }
    return i;
  }

  public static void createFieldAndAddAssignment(@NotNull Project project,
                                                 @NotNull PsiClass targetClass,
                                                 @NotNull PsiMethod method,
                                                 @NotNull PsiParameter parameter,
                                                 @NotNull PsiType fieldType,
                                                 @NotNull String fieldName,
                                                 boolean isStatic,
                                                 boolean isFinal) throws IncorrectOperationException {
    PsiManager psiManager = PsiManager.getInstance(project);
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(psiManager.getProject());
    PsiElementFactory factory = psiFacade.getElementFactory();

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


    Ref<PsiField> anchor = new Ref<>();
    AtomicBoolean isBefore = new AtomicBoolean();
    int i = findFieldAssignmentAnchor(statements, anchor, isBefore, targetClass, parameter);

    String stmtText = fieldName + " = " + parameter.getName() + ";";

    PsiVariable variable = psiFacade.getResolveHelper().resolveReferencedVariable(fieldName, methodBody);
    if (variable != null && !(variable instanceof PsiField)) {
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

    if (!anchor.isNull()) {
      PsiVariable psiVariable = anchor.get();
      psiVariable.normalizeDeclaration();
    }

    if (targetClass.findFieldByName(fieldName, false) == null) {
      if (!anchor.isNull()) {
        PsiField inField = anchor.get();
        if (isBefore.get()) {
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

  public static boolean isAvailable(@NotNull PsiParameter myParameter,
                                    @Nullable PsiType type,
                                    @Nullable PsiClass targetClass) {
    return isAvailable(myParameter, type, targetClass, true);
  }

  public static boolean isAvailable(@NotNull PsiParameter myParameter,
                                    @Nullable PsiType type, 
                                    @Nullable PsiClass targetClass, 
                                    boolean findIndirectAssignments) {
    return myParameter.isValid() &&
           BaseIntentionAction.canModify(myParameter) &&
           myParameter.getDeclarationScope() instanceof PsiMethod &&
           ((PsiMethod)myParameter.getDeclarationScope()).getBody() != null &&
           type != null &&
           type.isValid() &&
           targetClass != null &&
           !targetClass.isInterface() &&
           getParameterAssignedToField(myParameter, findIndirectAssignments) == null;
  }

  private FieldFromParameterUtils() { }
}
