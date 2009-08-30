/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiField;
import com.intellij.psi.impl.cache.InitializerTooLongException;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.stubs.NamedStub;
import org.jetbrains.annotations.NotNull;

public interface PsiFieldStub extends NamedStub<PsiField> {
  @NotNull TypeInfo getType(boolean doResolve);
  String getInitializerText() throws InitializerTooLongException;
  boolean isEnumConstant();
  boolean isDeprecated();
  boolean hasDeprecatedAnnotation();
}
