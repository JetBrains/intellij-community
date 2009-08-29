package com.intellij.psi;

import org.intellij.lang.annotations.Pattern;

@Pattern(PsiModifier.PUBLIC
           + "|" + PsiModifier.PROTECTED
           + "|" + PsiModifier.PRIVATE
           + "|" + PsiModifier.ABSTRACT
           + "|" + PsiModifier.FINAL
           + "|" + PsiModifier.NATIVE
           + "|" + PsiModifier.PACKAGE_LOCAL
           + "|" + PsiModifier.STATIC
           + "|" + PsiModifier.STRICTFP
           + "|" + PsiModifier.SYNCHRONIZED
           + "|" + PsiModifier.TRANSIENT
           + "|" + PsiModifier.VOLATILE
)
/**
 * Represents Java member modifier.
 * When String method or variable is annotated with this, 
 * the corresponding value must be one of the string constants defined in {@link com.intellij.psi.PsiModifier}
 */
public @interface Modifier {
}
