/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public interface IndexSink {
  <Psi extends PsiElement, K> void occurrence(@NotNull StubIndexKey<K, Psi> indexKey, @NotNull K value);
}
