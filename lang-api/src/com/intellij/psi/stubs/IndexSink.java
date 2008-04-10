/*
 * @author max
 */
package com.intellij.psi.stubs;

import org.jetbrains.annotations.NotNull;

public interface IndexSink {
  void occurrence(@NotNull StubIndexKey indexKey, @NotNull Object value);
}