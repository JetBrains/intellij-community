/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public interface NamedStub<T extends PsiNamedElement> extends StubElement<T> {
  @Indexed
  @NonNls
  @Nullable
  String getName();
}