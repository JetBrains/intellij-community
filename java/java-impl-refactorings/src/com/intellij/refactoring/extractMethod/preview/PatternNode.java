// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.preview;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

class PatternNode extends FragmentNode {
  PatternNode(PsiElement @NotNull [] elements) {
    super(elements[0], elements[elements.length - 1], new ExtractableFragment(elements));
  }
}
