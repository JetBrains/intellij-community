// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.psi.impl.source;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.stubs.StubTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A base interface for PSI files that may contain not only text-based syntactic trees as their content,
 * but also a more lightweight representation called stubs.
 * @see com.intellij.extapi.psi.StubBasedPsiElementBase
 */
public interface PsiFileWithStubSupport extends PsiFile {
  /**
   * @return the stub tree for this file, if it's stub-based at all. Will be null after the AST has been loaded
   * (e.g. by calling {@link PsiElement#getNode()} or {@link PsiElement#getText()}).
   */
  @Nullable
  StubTree getStubTree();

  /**
   * @return StubbedSpine for accessing stubbed PSI, which can be backed up by stubs or AST
   */
  default @NotNull StubbedSpine getStubbedSpine() {
    StubTree tree = getStubTree();
    if (tree == null) {
      throw new UnsupportedOperationException("Please implement getStubbedSpine method");
    }
    return tree.getSpine();
  }
}