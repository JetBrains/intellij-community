// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.typeMigration.rules;

import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.refactoring.typeMigration.TypeEvaluator;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.style.UnnecessarilyQualifiedStaticUsageInspection;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;

/**
 * @author anna
 */
public class RootTypeConversionRule extends TypeConversionRule {
  @Override
  public TypeConversionDescriptorBase findConversion(final PsiType from,
                                                     final PsiType to,
                                                     final PsiMember member,
                                                     final PsiExpression context,
                                                     final TypeMigrationLabeler labeler) {
    if (member != null && to instanceof PsiClassType && from instanceof PsiClassType) {
      final PsiClass targetClass = ((PsiClassType)to).resolve();
      if (targetClass != null && member.isPhysical()) {
        if (member instanceof PsiMethod) {
          PsiMethod method = (PsiMethod)member;
          PsiMethod replacer = targetClass.findMethodBySignature(method, true);
          if (replacer == null) {
            for (PsiMethod superMethod : method.findDeepestSuperMethods()) {
              replacer = targetClass.findMethodBySignature(superMethod, true);
              if (replacer != null) {
                method = superMethod;
                break;
              }
            }
          }
          if (replacer != null) {
            final boolean isStaticMethodConversion = replacer.hasModifierProperty(PsiModifier.STATIC);
            boolean isValid = isStaticMethodConversion ?
                              TypeConversionUtil.areTypesConvertible(method.getReturnType(), from) &&
                              TypeConversionUtil.areTypesConvertible(replacer.getReturnType(), to) :
                              TypeConversionUtil.areTypesConvertible(method.getReturnType(), replacer.getReturnType());
            if (isValid) {
              final PsiElement parent = context.getParent();
              if (context instanceof PsiMethodReferenceExpression) {
                final PsiType functionalInterfaceType = ((PsiMethodReferenceExpression)context).getFunctionalInterfaceType();
                if (Comparing.equal(functionalInterfaceType, to) && method.isEquivalentTo(LambdaUtil.getFunctionalInterfaceMethod(from))) {
                  return new TypeConversionDescriptorBase() {
                    @Override
                    public PsiExpression replace(PsiExpression expression, @NotNull TypeEvaluator evaluator) throws IncorrectOperationException {
                      final PsiMethodReferenceExpression methodReferenceExpression = (PsiMethodReferenceExpression)expression;
                      final PsiExpression qualifierExpression = methodReferenceExpression.getQualifierExpression();
                      if (qualifierExpression != null) {
                        return (PsiExpression)expression.replace(qualifierExpression);
                      }
                      else {
                        return expression;
                      }
                    }
                  };
                }
              }
              if (context instanceof PsiReferenceExpression && parent instanceof PsiMethodCallExpression) {
                final JavaResolveResult resolveResult = ((PsiReferenceExpression)context).advancedResolve(false);
                final PsiSubstitutor aSubst;
                final PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)parent).getMethodExpression();
                final PsiExpression qualifier = methodExpression.getQualifierExpression();
                final PsiClass substitutionClass = method.getContainingClass();
                if (qualifier != null) {
                  final PsiType evaluatedQualifierType = labeler.getTypeEvaluator().evaluateType(qualifier);
                  if (evaluatedQualifierType instanceof PsiClassType) {
                    aSubst = ((PsiClassType)evaluatedQualifierType).resolveGenerics().getSubstitutor();
                  }
                  else {
                    aSubst = PsiSubstitutor.EMPTY;
                  }
                }
                else {
                  aSubst = TypeConversionUtil.getClassSubstitutor(member.getContainingClass(), substitutionClass, PsiSubstitutor.EMPTY);
                }

                final PsiParameter[] originalParams = ((PsiMethod)member).getParameterList().getParameters();
                final PsiParameter[] migrationParams = replacer.getParameterList().getParameters();
                final PsiExpression[] actualParams = ((PsiMethodCallExpression)parent).getArgumentList().getExpressions();

                assert originalParams.length == migrationParams.length;
                final PsiSubstitutor methodTypeParamsSubstitutor =
                  labeler.getTypeEvaluator()
                    .createMethodSubstitution(originalParams, actualParams, method, context, aSubst != null ? aSubst : PsiSubstitutor.EMPTY,
                                              true);
                for (int i = 0; i < originalParams.length; i++) {
                  final PsiType originalType = resolveResult.getSubstitutor().substitute(originalParams[i].getType());

                  PsiType type = migrationParams[i].getType();
                  if (InheritanceUtil.isInheritorOrSelf(targetClass, substitutionClass, true)) {
                    final PsiSubstitutor superClassSubstitutor =
                      TypeConversionUtil.getClassSubstitutor(substitutionClass, targetClass, PsiSubstitutor.EMPTY);
                    assert (superClassSubstitutor != null);
                    type = superClassSubstitutor.substitute(type);
                  }

                  final PsiType migrationType = methodTypeParamsSubstitutor.substitute(type);
                  if (!originalType.equals(migrationType) && !areParametersAssignable(migrationType, i, actualParams)) {
                    if (migrationType instanceof PsiEllipsisType && actualParams.length != migrationParams.length) {
                      return null;
                    }
                    labeler.migrateExpressionType(actualParams[i], migrationType, context, false, true);
                  }
                }
              }
              return isStaticMethodConversion ? new MyStaticMethodConversionDescriptor(targetClass) : new TypeConversionDescriptorBase();
            }
          }
        }
        else if (member instanceof PsiField) {
          final PsiClass fieldContainingClass = member.getContainingClass();
          if (InheritanceUtil.isInheritorOrSelf(targetClass, fieldContainingClass, true)) {
            return new TypeConversionDescriptorBase();
          }
        }
      }
    }
    return null;
  }

  private static boolean areParametersAssignable(PsiType migrationType, int paramId, PsiExpression[] actualParams) {
    if (migrationType instanceof PsiEllipsisType) {
      if (actualParams.length == paramId) {
        // no arguments for ellipsis
        return true;
      }
      else if (actualParams.length == paramId + 1) {
        // only one argument for ellipsis
        return TypeConversionUtil.areTypesAssignmentCompatible(migrationType, actualParams[paramId]) ||
               TypeConversionUtil.areTypesAssignmentCompatible(((PsiEllipsisType)migrationType).getComponentType(), actualParams[paramId]);
      }
      else if (actualParams.length > paramId + 1) {
        // few arguments
        PsiType componentType = ((PsiEllipsisType)migrationType).getComponentType();
        for (int i = paramId; i < actualParams.length; i++) {
          if (!TypeConversionUtil.areTypesAssignmentCompatible(componentType, actualParams[i])) {
            return false;
          }
        }
        return true;
      }
      throw new AssertionError(" migrationType: " + migrationType + ", paramId: " + paramId + ", actualParameters: " + Arrays.toString(actualParams));
    } else {
      if (paramId >= actualParams.length) return true;
      return TypeConversionUtil.areTypesAssignmentCompatible(migrationType, actualParams[paramId]);
    }
  }

  private static final class MyStaticMethodConversionDescriptor extends TypeConversionDescriptorBase {
    private final @NotNull String myTargetClassQName;

    private MyStaticMethodConversionDescriptor(PsiClass replacer) {
      myTargetClassQName = Objects.requireNonNull(replacer.getQualifiedName());
    }

    @Override
    public PsiExpression replace(PsiExpression expression, @NotNull TypeEvaluator evaluator) throws IncorrectOperationException {
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
      final PsiExpression qualifierExpression = methodCallExpression.getMethodExpression().getQualifierExpression();
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(expression.getProject());
      final PsiMethodCallExpression newMethodCall;
      if (qualifierExpression != null) {
        JavaCodeStyleManager.getInstance(expression.getProject())
          .shortenClassReferences(qualifierExpression.replace(elementFactory.createExpressionFromText(myTargetClassQName, expression)));
        newMethodCall = methodCallExpression;
      }
      else {
        newMethodCall = (PsiMethodCallExpression)expression.replace(
          elementFactory.createExpressionFromText(myTargetClassQName + "." + expression.getText(), expression));
      }
      if (UnnecessarilyQualifiedStaticUsageInspection.isUnnecessarilyQualifiedAccess(newMethodCall.getMethodExpression(), false, false, false)) {
        Objects.requireNonNull(newMethodCall.getMethodExpression().getQualifierExpression()).delete();
      }
      return newMethodCall;
    }

    @Override
    public String toString() {
      return "Static method qualifier conversion -> " + myTargetClassQName;
    }
  }
}