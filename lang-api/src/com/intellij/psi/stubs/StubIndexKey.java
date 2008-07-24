/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.psi.PsiElement;
import com.intellij.util.indexing.ID;
import org.jetbrains.annotations.NonNls;

public final class StubIndexKey<K, Psi extends PsiElement> extends ID<K, Psi> {
  private StubIndexKey(@NonNls String name) {
    super(name);
  }

  public static <K, Psi extends PsiElement> StubIndexKey<K, Psi> createIndexKey(@NonNls String name) {
    return new StubIndexKey<K, Psi>(name);
  }

}