// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.lang.ASTFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FieldFromParameterUtils {
  private FieldFromParameterUtils() {}

  public static @Nullable PsiParameter findParameterAtOffset(@NotNull PsiFile file, int offset) {
    PsiParameterList parameterList = PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiParameterList.class, false);
    if (parameterList == null) return null;
    PsiParameter[] parameters = parameterList.getParameters();
    for (PsiParameter parameter : parameters) {
      TextRange range = parameter.getTextRange();
      if (range.getStartOffset() <= offset && offset <= range.getEndOffset()) return parameter;
    }
    return null;
  }

  public static @NotNull PsiType getType(@NotNull PsiParameter myParameter) {
    PsiType type = myParameter.getType();
    return type instanceof PsiEllipsisType ? ((PsiEllipsisType)type).toArrayType() : type;
  }

  public static @Nullable PsiType getSubstitutedType(@NotNull PsiParameter parameter) {
    PsiType type = getType(parameter);
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

    PsiType substitutedType = psiClass instanceof PsiTypeParameter
                              ? GenericsUtil.getVariableTypeByExpressionType(subst.substitute((PsiTypeParameter)psiClass))
                              : JavaPsiFacade.getElementFactory(parameter.getProject()).createType(psiClass, substitutor);
    if (substitutedType == null) return null;
    return PsiTypesUtil.createArrayType(substitutedType, type.getArrayDimensions());
  }

  public static @Nullable PsiField getParameterAssignedToField(@NotNull PsiParameter parameter) {
    return getParameterAssignedToField(parameter, true);
  }

  public static @Nullable PsiField getParameterAssignedToField(@NotNull PsiParameter parameter, boolean findIndirectAssignments) {
    for (PsiReference reference : ReferencesSearch.search(parameter, new LocalSearchScope(parameter.getDeclarationScope()), false)) {
      if (!(reference instanceof PsiReferenceExpression expression)) continue;
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
    if (statements.length == 0) return 0;
    PsiElement parent = statements[0].getParent().getParent();
    assert parent instanceof PsiMethod;
    PsiMethod method = (PsiMethod)parent;
    PsiMethodCallExpression constructorCall = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(method);
    int i = 0;
    for (; i < statements.length; i++) {
      PsiStatement psiStatement = statements[i];
      if (constructorCall != null && psiStatement.getTextOffset() <= constructorCall.getTextOffset()) {
        continue;
      }

      if (psiStatement instanceof PsiExpressionStatement expressionStatement) {
        PsiExpression expression = expressionStatement.getExpression();

        if (expression instanceof PsiAssignmentExpression assignmentExpression) {
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

  public static PsiField createFieldAndAddAssignment(@NotNull Project project,
                                                     @NotNull PsiClass targetClass,
                                                     @NotNull PsiMethod method,
                                                     @NotNull PsiParameter parameter,
                                                     @NotNull PsiType fieldType,
                                                     @NotNull String fieldName,
                                                     boolean isStatic,
                                                     boolean isFinal) {
    PsiManager psiManager = PsiManager.getInstance(project);
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(psiManager.getProject());
    PsiElementFactory factory = psiFacade.getElementFactory();

    PsiField field = factory.createField(fieldName, fieldType);

    PsiModifierList modifierList = field.getModifierList();
    if (modifierList == null) return null;
    modifierList.setModifierProperty(PsiModifier.STATIC, isStatic);
    modifierList.setModifierProperty(PsiModifier.FINAL, isFinal);

    NullableNotNullManager manager = NullableNotNullManager.getInstance(project);
    if (manager.copyNullableAnnotation(parameter, field) == null && isFinal) {
      manager.copyNotNullAnnotation(parameter, field);
    }

    PsiCodeBlock methodBody = method.getBody();
    if (methodBody == null) return null;
    PsiStatement[] statements = methodBody.getStatements();

    if (statements.length == 0) {
      PsiElement element = methodBody.getFirstBodyElement();
      if (element instanceof PsiWhiteSpace whiteSpace) {
        String text = whiteSpace.getText();
        int lastLineBreak = text.lastIndexOf('\n');
        if (lastLineBreak >= 0 && text.indexOf('\n') != lastLineBreak) {
          // At least two linebreaks in the body: remove last one
          text = text.substring(0, lastLineBreak);
          methodBody.getNode().replaceChild(whiteSpace.getNode(), ASTFactory.leaf(TokenType.WHITE_SPACE, text));
        }
      }
    }

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
          return (PsiField)targetClass.addBefore(field, inField);
        }
        else {
          return (PsiField)targetClass.addAfter(field, inField);
        }
      }
      else {
        return (PsiField)targetClass.add(field);
      }
    }
    return null;
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
    if (!myParameter.isValid() ||
        !BaseIntentionAction.canModify(myParameter) ||
        !(myParameter.getDeclarationScope() instanceof PsiMethod method)) {
      return false;
    }
    return method.getBody() != null &&
           type != null &&
           type.isValid() &&
           targetClass != null &&
           !targetClass.isInterface() &&
           (!targetClass.isRecord() || method.hasModifierProperty(PsiModifier.STATIC)) &&
           getParameterAssignedToField(myParameter, findIndirectAssignments) == null;
  }
}
