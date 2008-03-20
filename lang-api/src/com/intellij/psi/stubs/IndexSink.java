/*
 * @author max
 */
package com.intellij.psi.stubs;

import org.jetbrains.annotations.NotNull;

public interface IndexSink {
  void occurence(@NotNull StubIndexKey indexKey, @NotNull Object value);
}