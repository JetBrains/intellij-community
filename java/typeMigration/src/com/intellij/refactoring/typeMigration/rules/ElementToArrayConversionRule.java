/*
 * User: anna
 * Date: 25-Aug-2008
 */
package com.intellij.refactoring.typeMigration.rules;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;

public class ElementToArrayConversionRule extends TypeConversionRule{
  public TypeConversionDescriptorBase findConversion(final PsiType from, final PsiType to, final PsiMember member, final PsiExpression context,
                                                 final TypeMigrationLabeler labeler) {
    if (member == null && to instanceof PsiArrayType && TypeConversionUtil.isAssignable(((PsiArrayType)to).getComponentType(), from)) {
      TypeConversionDescriptor wrapDescription =
        new TypeConversionDescriptor("$qualifier$", "new " + from.getCanonicalText() + "[]{$qualifier$}", context);
      if (((PsiArrayType)to).getComponentType() instanceof PsiClassType && from instanceof PsiPrimitiveType) {
        final String boxedTypeName = ((PsiPrimitiveType)from).getBoxedTypeName();
        final String normalizedArrayInitializer =
          PsiUtil.isLanguageLevel5OrHigher(context) ? "$qualifier$" : boxedTypeName + ".valueOf($qualifier$)";
        wrapDescription = new TypeConversionDescriptor("$qualifier$", "new " + boxedTypeName + "[]{" + normalizedArrayInitializer + "}", context);
      }
      final PsiElement parent = context.getParent();
      if ((context instanceof PsiLiteralExpression || context instanceof PsiReferenceExpression) && parent instanceof PsiStatement) {
        return wrapDescription;
      }
      if (parent instanceof PsiAssignmentExpression && ((PsiAssignmentExpression)parent).getRExpression() == context) {
        return wrapDescription;
      }
    }
    return null;
  }
}