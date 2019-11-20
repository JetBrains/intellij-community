// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiRecordHeader;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import org.jetbrains.annotations.NotNull;

public class PsiRecordHeaderImpl extends CompositePsiElement implements PsiRecordHeader {
  public PsiRecordHeaderImpl() {
    super(JavaElementType.RECORD_HEADER);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitRecordHeader(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiRecordHeader";
  }
}
