// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting;

import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.impl.light.LightRecordCanonicalConstructor;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.BitUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

final class ControlFlowChecker {
  private final @NotNull JavaErrorVisitor myVisitor;
  // map codeBlock->List of PsiReferenceExpression of uninitialized final variables
  private final Map<PsiElement, Collection<PsiReferenceExpression>> myUninitializedVarProblems = new HashMap<>();
  // map codeBlock->List of PsiReferenceExpression of extra initialization of final variable
  private final Map<PsiElement, Collection<ControlFlowUtil.VariableInfo>> myFinalVarProblems = new HashMap<>();

  ControlFlowChecker(@NotNull JavaErrorVisitor visitor) { myVisitor = visitor; }

  void checkMissingReturn(@NotNull PsiCodeBlock codeBlock) {
    PsiElement gParent = codeBlock.getParent();
    PsiType returnType;
    if (gParent instanceof PsiMethod method) {
      returnType = method.getReturnType();
    }
    else if (gParent instanceof PsiLambdaExpression lambdaExpression) {
      returnType = LambdaUtil.getFunctionalInterfaceReturnType(lambdaExpression);
    }
    else {
      return;
    }
    if (returnType == null || PsiTypes.voidType().equals(returnType.getDeepComponentType())) return;
    if (!(codeBlock.getParent() instanceof PsiParameterListOwner owner)) return;

    // do not compute constant expressions for if() statement condition
    // see JLS 14.20 Unreachable Statements
    try {
      ControlFlow controlFlow = ControlFlowFactory.getControlFlowNoConstantEvaluate(codeBlock);
      if (!ControlFlowUtil.returnPresent(controlFlow)) {
        PsiJavaToken rBrace = codeBlock.getRBrace();
        PsiElement context = rBrace == null ? codeBlock.getLastChild() : rBrace;
        myVisitor.report(JavaErrorKinds.RETURN_MISSING.create(context, owner));
      }
    }
    catch (AnalysisCanceledException ignored) { }
  }

  void checkUnreachableStatement(@Nullable PsiCodeBlock codeBlock) {
    if (codeBlock == null) return;
    // do not compute constant expressions for if() statement condition
    // see JLS 14.20 Unreachable Statements
    try {
      AllVariablesControlFlowPolicy policy = AllVariablesControlFlowPolicy.getInstance();
      ControlFlow controlFlow = ControlFlowFactory.getControlFlow(codeBlock, policy, ControlFlowOptions.NO_CONST_EVALUATE);
      PsiElement unreachableStatement = ControlFlowUtil.getUnreachableStatement(controlFlow);
      if (unreachableStatement != null) {
        if (unreachableStatement instanceof PsiCodeBlock && unreachableStatement.getParent() instanceof PsiBlockStatement) {
          unreachableStatement = unreachableStatement.getParent();
        }
        if (unreachableStatement instanceof PsiStatement) {
          PsiElement parent = unreachableStatement.getParent();
          if (parent instanceof PsiWhileStatement || parent instanceof PsiForStatement) {
            PsiExpression condition = ((PsiConditionalLoopStatement)parent).getCondition();
            PsiConstantEvaluationHelper evaluator = JavaPsiFacade.getInstance(myVisitor.project()).getConstantEvaluationHelper();
            if (Boolean.FALSE.equals(evaluator.computeConstantExpression(condition))) {
              myVisitor.report(JavaErrorKinds.STATEMENT_UNREACHABLE_LOOP_BODY.create(condition));
              return;
            }
          }
        }
        myVisitor.report(JavaErrorKinds.STATEMENT_UNREACHABLE.create(unreachableStatement));
      }
    }
    catch (AnalysisCanceledException | IndexNotReadyException e) {
      // incomplete code
    }
  }

  void checkInitializerCompleteNormally(@NotNull PsiClassInitializer initializer) {
    PsiCodeBlock body = initializer.getBody();
    // unhandled exceptions already reported
    try {
      ControlFlow controlFlow = ControlFlowFactory.getControlFlowNoConstantEvaluate(body);
      int completionReasons = ControlFlowUtil.getCompletionReasons(controlFlow, 0, controlFlow.getSize());
      if (!BitUtil.isSet(completionReasons, ControlFlowUtil.NORMAL_COMPLETION_REASON)) {
        myVisitor.report(JavaErrorKinds.CLASS_INITIALIZER_MUST_COMPLETE_NORMALLY.create(body));
      }
    }
    catch (AnalysisCanceledException e) {
      // incomplete code
    }
  }

  void checkVariableMustBeFinal(@NotNull PsiVariable variable, @NotNull PsiJavaCodeReferenceElement context) {
    if (variable.hasModifierProperty(PsiModifier.FINAL)) return;
    PsiElement scope = ControlFlowUtil.getScopeEnforcingEffectiveFinality(variable, context);
    if (scope == null) return;
    if (scope instanceof PsiClass) {
      if (variable instanceof PsiParameter parameter) {
        PsiElement parent = variable.getParent();
        if (parent instanceof PsiParameterList && parent.getParent() instanceof PsiLambdaExpression &&
            ControlFlowUtil.isEffectivelyFinal(variable, parameter.getDeclarationScope())) {
          return;
        }
      }
      boolean isToBeEffectivelyFinal = myVisitor.isApplicable(JavaFeature.EFFECTIVELY_FINAL);
      if (isToBeEffectivelyFinal && ControlFlowUtil.isEffectivelyFinal(variable, scope, context)) return;
      var kind = isToBeEffectivelyFinal ? JavaErrorKinds.VARIABLE_MUST_BE_EFFECTIVELY_FINAL : JavaErrorKinds.VARIABLE_MUST_BE_FINAL;
      myVisitor.report(kind.create(context, variable));
    } else if (scope instanceof PsiLambdaExpression) {
      if (ControlFlowUtil.isEffectivelyFinal(variable, scope, context)) return;
      myVisitor.report(JavaErrorKinds.VARIABLE_MUST_BE_EFFECTIVELY_FINAL_LAMBDA.create(context, variable));
    } else if (scope instanceof PsiSwitchLabelStatementBase) {
      // Reported separately in ExpressionChecker.checkOutsideDeclaredCantBeAssignmentInGuard
      if (context instanceof PsiReferenceExpression ref && PsiUtil.isAccessedForWriting(ref)) return;
      if (ControlFlowUtil.isEffectivelyFinal(variable, scope, context)) return;
      myVisitor.report(JavaErrorKinds.VARIABLE_MUST_BE_EFFECTIVELY_FINAL_GUARD.create(context, variable));
    }
  }

  void checkFinalFieldInitialized(@NotNull PsiField field) {
    if (!field.hasModifierProperty(PsiModifier.FINAL)) return;
    if (ControlFlowUtil.isFieldInitializedAfterObjectConstruction(field)) return;
    if (PsiUtilCore.hasErrorElementChild(field)) return;
    myVisitor.report(JavaErrorKinds.FIELD_NOT_INITIALIZED.create(field));
  }

  void checkVariableInitializedBeforeUsage(@NotNull PsiVariable variable, @NotNull PsiReferenceExpression expression) {
    if (ControlFlowUtil.isInitializedBeforeUsage(expression, variable, myUninitializedVarProblems, false)) {
      return;
    }
    myVisitor.report(JavaErrorKinds.VARIABLE_NOT_INITIALIZED.create(expression, variable));
  }

  void checkFinalVariableMightAlreadyHaveBeenAssignedTo(@NotNull PsiVariable variable, @NotNull PsiReferenceExpression expression) {
    ControlFlowUtil.DoubleInitializationProblem
      problem = ControlFlowUtil.findFinalVariableAlreadyInitializedProblem(variable, expression, myFinalVarProblems);
    var kind = switch (problem) {
      case NORMAL -> JavaErrorKinds.VARIABLE_ALREADY_ASSIGNED;
      case IN_LOOP -> JavaErrorKinds.VARIABLE_ASSIGNED_IN_LOOP;
      case IN_CONSTRUCTOR -> JavaErrorKinds.VARIABLE_ALREADY_ASSIGNED_CONSTRUCTOR;
      case IN_FIELD_INITIALIZER -> JavaErrorKinds.VARIABLE_ALREADY_ASSIGNED_FIELD;
      case IN_INITIALIZER -> JavaErrorKinds.VARIABLE_ALREADY_ASSIGNED_INITIALIZER;
      case NO_PROBLEM -> null;
    };
    if (kind == null) return;
    myVisitor.report(kind.create(expression, variable));
  }

  /**
   * @return field that has initializer with this element as subexpression or null if not found
   */
  private static PsiField findEnclosingFieldInitializer(@NotNull PsiElement entry) {
    PsiElement element = entry;
    while (element != null) {
      PsiElement parent = element.getParent();
      if (parent instanceof PsiField field) {
        if (element == field.getInitializer()) return field;
        if (field instanceof PsiEnumConstant enumConstant && element == enumConstant.getArgumentList()) return field;
      }
      if (element instanceof PsiClass || element instanceof PsiMethod) return null;
      element = parent;
    }
    return null;
  }

  private static boolean isSameField(@NotNull PsiMember enclosingCtrOrInitializer,
                                     @NotNull PsiField field,
                                     @NotNull PsiReferenceExpression reference,
                                     @NotNull PsiFile containingFile) {
    if (!containingFile.getManager().areElementsEquivalent(enclosingCtrOrInitializer.getContainingClass(), field.getContainingClass())) return false;
    return LocalsOrMyInstanceFieldsControlFlowPolicy.isLocalOrMyInstanceReference(reference);
  }

  private static boolean canWriteToFinal(@NotNull PsiVariable variable,
                                         @NotNull PsiExpression expression,
                                         @NotNull PsiReferenceExpression reference,
                                         @NotNull PsiFile containingFile) {
    if (variable.hasInitializer()) {
      return variable instanceof PsiField field && !PsiAugmentProvider.canTrustFieldInitializer(field);
    }
    if (variable instanceof PsiParameter) return false;
    PsiElement scope = ControlFlowUtil.getScopeEnforcingEffectiveFinality(variable, expression);
    if (variable instanceof PsiField field) {
      // if inside some field initializer
      if (findEnclosingFieldInitializer(expression) != null) return true;
      PsiClass containingClass = field.getContainingClass();
      if (containingClass == null) return true;
      // assignment from within inner class is illegal always
      if (scope != null && !containingFile.getManager().areElementsEquivalent(scope, containingClass)) return false;
      PsiMember enclosingCtrOrInitializer = PsiUtil.findEnclosingConstructorOrInitializer(expression);
      return enclosingCtrOrInitializer != null &&
             !(enclosingCtrOrInitializer instanceof PsiMethod method &&
               JavaPsiRecordUtil.isCompactConstructor(method) &&
               containingClass.isRecord()) &&
             isSameField(enclosingCtrOrInitializer, field, reference, containingFile);
    }
    if (variable instanceof PsiLocalVariable) {
      boolean isAccessedFromOtherClass = scope != null;
      return !isAccessedFromOtherClass;
    }
    return true;
  }

  private static boolean hasWriteToFinalInsideLambda(@NotNull PsiVariable variable, @NotNull PsiJavaCodeReferenceElement context) {
    return hasWriteToFinalInsideLambda(variable, PsiTreeUtil.getParentOfType(context, PsiLambdaExpression.class), context);
  }

  @Contract("_, null, _ -> false")
  private static boolean hasWriteToFinalInsideLambda(@NotNull PsiVariable variable,
                                                     @Nullable PsiLambdaExpression lambdaExpression,
                                                     @NotNull PsiJavaCodeReferenceElement context) {
    if (lambdaExpression == null) return false;
    if (!PsiTreeUtil.isAncestor(lambdaExpression, variable, true)) {
      PsiElement parent = variable.getParent();
      if (parent instanceof PsiParameterList && parent.getParent() == lambdaExpression) {
        return false;
      }
      PsiSwitchLabelStatementBase label =
        PsiTreeUtil.getParentOfType(context, PsiSwitchLabelStatementBase.class, true, PsiLambdaExpression.class);
      if (label != null && PsiTreeUtil.isAncestor(label.getGuardExpression(), context, false)) {
        return false;
      }
      return !ControlFlowUtil.isEffectivelyFinal(variable, lambdaExpression, context);
    }
    return false;
  }

  void checkCannotWriteToFinal(@NotNull PsiExpression expression) {
    PsiExpression operand = null;
    if (expression instanceof PsiAssignmentExpression assignment) {
      operand = assignment.getLExpression();
    }
    else if (PsiUtil.isIncrementDecrementOperation(expression)) {
      operand = ((PsiUnaryExpression)expression).getOperand();
    }
    if (!(PsiUtil.skipParenthesizedExprDown(operand) instanceof PsiReferenceExpression reference)) return;
    if (!(reference.resolve() instanceof PsiVariable variable)) return;
    if (!variable.hasModifierProperty(PsiModifier.FINAL)) return;
    boolean canWrite = canWriteToFinal(variable, expression, reference, myVisitor.file()) && 
                       !hasWriteToFinalInsideLambda(variable, reference);
    if (canWrite) return;
    myVisitor.report(JavaErrorKinds.ASSIGNMENT_TO_FINAL_VARIABLE.create(reference, variable));
  }

  static @Nullable ControlFlow getControlFlow(@NotNull PsiElement context) {
    LocalsOrMyInstanceFieldsControlFlowPolicy policy = LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance();
    try {
      return ControlFlowFactory.getControlFlow(context, policy, ControlFlowOptions.create(true, true, true));
    }
    catch (AnalysisCanceledException e) {
      return null;
    }
  }

  void checkRecordComponentInitialized(@NotNull PsiRecordComponent component) {
    PsiClass aClass = component.getContainingClass();
    if (aClass == null) return;
    if (component.getNameIdentifier() == null) return;
    PsiMethod canonicalConstructor = JavaPsiRecordUtil.findCanonicalConstructor(aClass);
    if (canonicalConstructor == null || canonicalConstructor instanceof LightRecordCanonicalConstructor) return;
    if (JavaPsiRecordUtil.isCompactConstructor(canonicalConstructor)) return;
    PsiCodeBlock body = canonicalConstructor.getBody();
    if (body == null) return;
    PsiField field = JavaPsiRecordUtil.getFieldForComponent(component);
    if (field == null) return;
    ControlFlow flow = getControlFlow(body);
    if (flow == null || ControlFlowUtil.isVariableDefinitelyAssigned(field, flow)) return;
    myVisitor.report(JavaErrorKinds.RECORD_COMPONENT_NOT_INITIALIZED.create(component));
  }
}
