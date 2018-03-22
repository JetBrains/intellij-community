// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dupLocator.iterators;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public abstract class NodeIterator implements Cloneable {
  public abstract boolean hasNext();
  public abstract PsiElement current();
  public abstract void advance();
  public abstract void rewind();
  public abstract void reset();

  public void rewind(int number) {
    while(number > 0) {
      --number;
      rewind();
    }
  }

  public void rewindTo(@NotNull PsiElement element) {
    while (current() != element) {
      rewind();
    }
  }

  public NodeIterator clone() {
    try {
      return (NodeIterator) super.clone();
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException();
    }
  }
}
