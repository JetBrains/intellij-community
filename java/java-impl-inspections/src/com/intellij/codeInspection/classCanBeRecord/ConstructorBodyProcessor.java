// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.classCanBeRecord;

import com.intellij.openapi.util.Ref;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.*;

import static com.intellij.psi.PsiModifier.STATIC;

@NotNullByDefault
final class ConstructorBodyProcessor {
  private final PsiMethod constructor;
  private final Map<PsiParameter, @Nullable PsiField> paramsToFields = new HashMap<>();
  // TODO(bartekpacia): change type to SequencedMap once we move to Java 21
  private final LinkedHashMap<String, PsiExpression> fieldNamesToInitializers = new LinkedHashMap<>();
  private final List<PsiField> instanceFields;

  private boolean canonical = false;
  private boolean delegating = false;
  private boolean hasUnresolvedRefs = false;
  private boolean tooComplex = false;

  /// Even if false, it doesn't necessarily mean that conversion to record isn't possible.
  ///
  /// It is OK to have statements before all fields are assigned if:
  ///  - this constructor is a canonical constructor, OR
  ///  - (JDK 25+) this statement is in constructor prologue (see JEP 513)
  private boolean statementsBeforeAllFieldsAssigned = false;
  private final List<PsiStatement> otherStatements = new ArrayList<>();
  private final MultiMap<PsiField, PsiParameter> fieldsToParams = new MultiMap<>();

  ConstructorBodyProcessor(PsiMethod constructor,
                           List<PsiField> instanceFields) {
    this.constructor = constructor;
    this.instanceFields = instanceFields;
    final PsiCodeBlock body = Objects.requireNonNull(constructor.getBody(), "constructor must have body");
    for (PsiStatement statement : body.getStatements()) {
      execute(statement);
    }

    postprocess();
  }

  private void execute(PsiStatement statement) {
    if (!(statement instanceof PsiExpressionStatement expressionStatement)) {
      otherStatements.add(statement);
      return;
    }
    final PsiExpression expression = expressionStatement.getExpression();

    final PsiClass containingClass = constructor.getContainingClass();
    if (containingClass == null) return;

    if (expression instanceof PsiMethodCallExpression methodCallExpr && JavaPsiConstructorUtil.isChainedConstructorCall(methodCallExpr)) {
      delegating = true;
      for (PsiExpression arg : methodCallExpr.getArgumentList().getExpressions()) {
        if (hasReferenceToContainingClass(containingClass, arg)) {
          statementsBeforeAllFieldsAssigned = true;
        }
      }
      return;
    }

    // Is it an assignment expression to an instance field?
    if (!expressionIsAssignmentToInstanceField(expression) && !delegating) {
      // It is NOT an assignment expression to an instance field.
      // This means that:
      //  - all instance variables must be already initialized, OR
      //  - (JDK 25+) this statement is inside early construction context, more specifically: in constructor prologue (see JEP 513).
      //      This means that it must NOT use 'this', either implicitly or explicitly, except for simple assignment statements.

      otherStatements.add(statement);
      if (fieldNamesToInitializers.isEmpty() && PsiUtil.isAvailable(JavaFeature.STATEMENTS_BEFORE_SUPER, statement)) {
        PsiExpression exprToConsider = expression instanceof PsiAssignmentExpression assignExpr ? assignExpr.getRExpression() : expression;
        if (hasReferenceToContainingClass(containingClass, exprToConsider)) {
          statementsBeforeAllFieldsAssigned = true;
        }
      }
      else if (fieldNamesToInitializers.size() < instanceFields.size()) {
        // If not all instance fields are assigned up to this point,
        // then this constructor cannot be converted to a non-canonical record constructor.
        statementsBeforeAllFieldsAssigned = true;
      }
      return;
    }

    if (!(expression instanceof PsiAssignmentExpression assignExpr)) return;

    if (!(assignExpr.getLExpression() instanceof PsiReferenceExpression leftRefExpr)) return;
    if (leftRefExpr.resolve() == null) {
      hasUnresolvedRefs = true;
      return;
    }
    if (!(leftRefExpr.resolve() instanceof PsiField field)) return;

    final PsiExpression rightExpr = assignExpr.getRExpression();
    if (rightExpr == null) return;
    final PsiType targetType = leftRefExpr.getType();
    final PsiType assignedType = rightExpr.getType();
    if (targetType == null || assignedType == null || !targetType.isAssignableFrom(assignedType)) {
      hasUnresolvedRefs = true; // "Overload" semantics of this flag
      return;
    }


    fieldNamesToInitializers.put(field.getName(), rightExpr);

    Ref<@Nullable PsiParameter> refParameterForField = new Ref<>();
    rightExpr.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        final PsiElement resolved = expression.resolve();
        if (resolved == null) {
          hasUnresolvedRefs = true;
        }
        else if (resolved instanceof PsiParameter parameter) {
          fieldsToParams.putValue(field, parameter);
          if (!paramsToFields.containsKey(parameter)) {
            refParameterForField.set(parameter);
          }
        }
      }
    });
    PsiParameter parameterForField = refParameterForField.get();
    if (parameterForField != null) {
      paramsToFields.put(parameterForField, field);
    }
  }

  /// Must be called after visiting is done.
  private void postprocess() {
    for (PsiParameter parameter : constructor.getParameterList().getParameters()) {
      if (paramsToFields.get(parameter) == null) {
        paramsToFields.put(parameter, null);
      }
    }


    // if "a constructor parameter is referenced from more than a single instance field assignment",
    // then we cannot convert it because of ambiguities
    for (PsiParameter parameter : fieldsToParams.values()) {
      if (fieldsToParams.keySet().stream().filter(field -> fieldsToParams.get(field).contains(parameter)).count() > 1) {
        tooComplex = true;
        return;
      }
    }


    // If the constructor is not delegating, then it must assign all instance fields.
    // If it doesn't do it, it is not valid.
    if (!delegating) {
      assert constructor.getBody() != null; // The caller asserts this
      PsiParameter[] ctorParams = constructor.getParameterList().getParameters();
      if (allFieldsAssignedIn(constructor.getBody(), instanceFields)) {
        if (ctorParams.length == instanceFields.size()) {
          assert constructor.getContainingClass() != null; // The caller asserts this 
          if (constructor.getContainingClass().getConstructors().length == 1) {
            // If there is just a single constructor, even if the order and types of its parameters don't exactly match the
            // order of instance fields, we can still convert it to a canonical constructor using the paramsToFields map.
            canonical = true;
          }
          else {
            boolean fieldsMatchInOrder = true;
            for (int i = 0; i < ctorParams.length; i++) {
              PsiType ctorParamType = ctorParams[i].getType();
              PsiType instanceFieldType = instanceFields.get(i).getType();
              if (!TypeConversionUtil.isAssignable(instanceFieldType, ctorParamType)) {
                fieldsMatchInOrder = false;
                break;
              }
            }

            if (fieldsMatchInOrder) {
              canonical = true;
            }
          }
        }
        else {
          // Not canonical and not delegating: just a custom constructor that assigns all instance fields.
        }
      }
      else {
        // If the constructor is not delegating, then it must assign all instance fields.
        // Otherwise it is invalid - and if we are here, we hit this exact case.
        hasUnresolvedRefs = true; // "Overload" semantics of this flag
      }
    }
  }

  boolean isCanonical() {
    return canonical;
  }

  boolean isDelegating() {
    return delegating;
  }

  boolean hasUnresolvedRefs() {
    return hasUnresolvedRefs;
  }

  boolean isTooComplex() {
    return tooComplex;
  }

  boolean hasAnyStatementBeforeAllFieldsAreAssigned() {
    return statementsBeforeAllFieldsAssigned;
  }

  Map<PsiParameter, @Nullable PsiField> getParamsToFields() {
    return paramsToFields;
  }

  LinkedHashMap<String, PsiExpression> getFieldNamesToInitializers() {
    return fieldNamesToInitializers;
  }

  @UnmodifiableView
  List<PsiStatement> getOtherStatements() {
    return Collections.unmodifiableList(otherStatements);
  }

  private static boolean allFieldsAssignedIn(PsiCodeBlock block, List<PsiField> instanceFields) {
    for (PsiField instanceField : instanceFields) {
      if (!ControlFlowUtil.variableDefinitelyAssignedIn(instanceField, block)) {
        return false;
      }
    }
    return true;
  }

  private static boolean expressionIsAssignmentToInstanceField(PsiExpression expr) {
    if (!(expr instanceof PsiAssignmentExpression assignExpr)) return false;
    PsiExpression leftExpr = assignExpr.getLExpression();
    if (!(leftExpr instanceof PsiReferenceExpression referenceExpr)) return false;
    PsiElement resolved = referenceExpr.resolve();
    return resolved instanceof PsiField psiField && !psiField.hasModifierProperty(STATIC);
  }

  private static boolean hasReferenceToContainingClass(PsiClass containingClass, @Nullable PsiExpression expression) {
    if (expression == null) return false;
    Ref<Boolean> hasReferenceToClassUnderConstruction = new Ref<>(false);
    expression.accept(new JavaRecursiveElementWalkingVisitor() {
      void markInvalid() {
        hasReferenceToClassUnderConstruction.set(true);
        stopWalking();
      }

      @Override
      public void visitClass(PsiClass aClass) {
        if (aClass instanceof PsiAnonymousClass anonymousClass) {
          PsiExpressionList arguments = anonymousClass.getArgumentList();
          if (arguments != null) {
            for (PsiExpression expression : arguments.getExpressions()) {
              if (hasReferenceToContainingClass(containingClass, expression)) {
                markInvalid();
                return;
              }
            }
          }
        }
      }

      @Override
      public void visitThisExpression(PsiThisExpression expression) {
        super.visitThisExpression(expression);
        markInvalid();
      }

      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);

        if (expression.getQualifier() != null) {
          return;
        }

        PsiElement resolved = expression.resolve();
        if (resolved == null) {
          markInvalid();
          return;
        }

        if (resolved instanceof PsiField field && !field.hasModifierProperty(STATIC) && field.getContainingClass() == containingClass) {
          markInvalid();
        }
        else if (resolved instanceof PsiMethod method && !method.hasModifierProperty(STATIC)) {
          if (InheritanceUtil.isInheritorOrSelf(containingClass, method.getContainingClass(), true)) {
            markInvalid();
          }
        }
      }
    });
    return hasReferenceToClassUnderConstruction.get();
  }
}
