/*
 * User: anna
 * Date: 08-Aug-2008
 */
package com.intellij.refactoring.typeMigration.rules;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;

public class RootTypeConversionRule extends TypeConversionRule {

  public TypeConversionDescriptorBase findConversion(final PsiType from, final PsiType to, final PsiMember member, final PsiExpression context,
                                                 final TypeMigrationLabeler labeler) {
    if (to instanceof PsiClassType && from instanceof PsiClassType) {
      final PsiClass targetClass = ((PsiClassType)to).resolve();
      if (targetClass != null && member instanceof PsiMethod && member.isPhysical()) {
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
        if (replacer != null && TypeConversionUtil.areTypesConvertible(method.getReturnType(), replacer.getReturnType())) {
          final PsiElement parent = context.getParent();
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
              labeler.getTypeEvaluator().createMethodSubstitution(originalParams, actualParams, method, (PsiReferenceExpression)context, aSubst != null ? aSubst : PsiSubstitutor.EMPTY, true);
            for (int i = 0; i < originalParams.length; i++) {
              final PsiType originalType = resolveResult.getSubstitutor().substitute(originalParams[i].getType());

              PsiType type = migrationParams[i].getType();
              if (InheritanceUtil.isInheritorOrSelf(targetClass, substitutionClass, true)) {
                final PsiSubstitutor superClassSubstitutor =
                  TypeConversionUtil.getClassSubstitutor(substitutionClass, targetClass, PsiSubstitutor.EMPTY);
                assert (superClassSubstitutor != null);
                type = superClassSubstitutor.substitute(type);
              }

              if (!originalType.equals(type)) {
                labeler.migrateExpressionType(actualParams[i], methodTypeParamsSubstitutor.substitute(type), context, originalType.equals(type), true);
              }
            }
          }
          return new TypeConversionDescriptorBase();
        }
      }
    }
    return null;
  }
}