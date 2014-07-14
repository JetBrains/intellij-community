/*
 * User: anna
 * Date: 08-Aug-2008
 */
package com.intellij.refactoring.typeMigration.rules;

import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;

public class BoxingTypeConversionRule extends TypeConversionRule {

  public TypeConversionDescriptorBase findConversion(final PsiType from, final PsiType to, final PsiMember member, final PsiExpression context,
                                                 final TypeMigrationLabeler labeler) {
    if (to instanceof PsiClassType && from instanceof PsiPrimitiveType) {
      if (!PsiUtil.isLanguageLevel5OrHigher(context)) {
        final String boxedTypeName = ((PsiPrimitiveType)from).getBoxedTypeName();
        if (Comparing.strEqual(boxedTypeName, to.getCanonicalText())) {
          return new TypeConversionDescriptor("$qualifier$", boxedTypeName + ".valueOf($qualifier$)");
        }
      }
    }
    else if (from instanceof PsiClassType && to instanceof PsiPrimitiveType) {
      if (!PsiUtil.isLanguageLevel5OrHigher(context)) {
        final String boxedTypeName = ((PsiPrimitiveType)to).getBoxedTypeName();
        if (Comparing.strEqual(boxedTypeName, from.getCanonicalText())) {
          return new TypeConversionDescriptor("$qualifier$", "($qualifier$)." + to.getCanonicalText() + "Value()");
        }
      }
    }
    return null;
  }
}