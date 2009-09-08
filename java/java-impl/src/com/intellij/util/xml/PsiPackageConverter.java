package com.intellij.util.xml;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.PackageReferenceSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public class PsiPackageConverter extends Converter<PsiPackage> implements CustomReferenceConverter<PsiPackage> {
  public PsiPackage fromString(@Nullable @NonNls String s, final ConvertContext context) {
    if (s == null) return null;
    return JavaPsiFacade.getInstance(context.getPsiManager().getProject()).findPackage(s);
  }

  public String toString(@Nullable PsiPackage psiPackage, final ConvertContext context) {
    return psiPackage == null ? null : psiPackage.getQualifiedName();
  }

  @NotNull
  public PsiReference[] createReferences(GenericDomValue<PsiPackage> genericDomValue, PsiElement element, ConvertContext context) {
    final String s = genericDomValue.getStringValue();
    if (s == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    return new PackageReferenceSet(s, element, ElementManipulators.getOffsetInElement(element)).getPsiReferences();
  }
}
