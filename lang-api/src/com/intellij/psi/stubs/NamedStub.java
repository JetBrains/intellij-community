/*
 * @author max
 */
package com.intellij.psi.stubs;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public interface NamedStub extends StubElement {
  @Indexed
  @Stubbed
  @NonNls
  @Nullable
  String getName();
}