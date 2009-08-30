/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiParameter;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.stubs.NamedStub;
import org.jetbrains.annotations.NotNull;

public interface PsiParameterStub extends NamedStub<PsiParameter> {
  boolean isParameterTypeEllipsis();
  @NotNull TypeInfo getType(boolean doResolve);
  PsiModifierListStub getModList();
}