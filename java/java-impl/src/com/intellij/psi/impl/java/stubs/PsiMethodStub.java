/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.stubs.NamedStub;
import org.jetbrains.annotations.NotNull;

public interface PsiMethodStub extends NamedStub<PsiMethod> {
  boolean isConstructor();
  boolean isVarArgs();
  boolean isAnnotationMethod();

  String getDefaultValueText();
  @NotNull TypeInfo getReturnTypeText(boolean doResolve);

  boolean isDeprecated();
  boolean hasDeprecatedAnnotation();

  PsiParameterStub findParameter(int idx);
}
