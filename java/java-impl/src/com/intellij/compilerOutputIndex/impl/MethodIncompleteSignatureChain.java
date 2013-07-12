package com.intellij.compilerOutputIndex.impl;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class MethodIncompleteSignatureChain {
  private final List<MethodIncompleteSignature> myMethodIncompleteSignatures;

  public MethodIncompleteSignatureChain(final List<MethodIncompleteSignature> methodIncompleteSignatures) {
    myMethodIncompleteSignatures = methodIncompleteSignatures;
  }

  public List<MethodIncompleteSignature> list() {
    return myMethodIncompleteSignatures;
  }

  public boolean isEmpty() {
    return myMethodIncompleteSignatures.isEmpty();
  }

  @Nullable
  public MethodIncompleteSignature getFirstInvocation() {
    final int size = myMethodIncompleteSignatures.size();
    return size == 0 ? null : myMethodIncompleteSignatures.get(0);
  }

  @Nullable
  public MethodIncompleteSignature getLastInvocation() {
    final int size = myMethodIncompleteSignatures.size();
    return size == 0 ? null : myMethodIncompleteSignatures.get(size -1);
  }

  public int size() {
    return myMethodIncompleteSignatures.size();
  }

  public MethodIncompleteSignature get(final int index) {
    return myMethodIncompleteSignatures.get(index);
  }
}
