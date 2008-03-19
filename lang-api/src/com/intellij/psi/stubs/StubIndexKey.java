/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;

public final class StubIndexKey<K, Psi extends PsiElement> {
  private static int ourCounter = 0;

  private final int myIndex = ourCounter++;
  private final String myName;

  public StubIndexKey(@NonNls String name) {
    myName = name;
  }

  public static <K, Psi extends PsiElement> StubIndexKey<K, Psi> create(@NonNls String name) {
    return new StubIndexKey<K, Psi>(name);
  }

  public int hashCode() {
    return myIndex;
  }

  public String toString() {
    return myName;
  }
}