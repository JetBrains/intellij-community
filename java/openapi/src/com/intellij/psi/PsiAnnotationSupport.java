package com.intellij.psi;

import org.jetbrains.annotations.NotNull;

/**
 * @author Serega.Vasiliev
 */
public interface PsiAnnotationSupport {

  @NotNull
  PsiLiteral createLiteralValue(@NotNull String value, @NotNull PsiElement context);

  /*@NotNull
  PsiArrayInitializerMemberValue createArrayMemberValue(@Nullable PsiElement context);

  @NotNull
  PsiAnnotation createAnnotation(@NotNull String qname, @Nullable PsiElement context);*/
}
