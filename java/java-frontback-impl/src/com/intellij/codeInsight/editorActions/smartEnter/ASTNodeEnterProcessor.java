// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import org.jetbrains.annotations.NotNull;

public interface ASTNodeEnterProcessor extends EnterProcessor {
  boolean doEnter(@NotNull Editor editor, @NotNull ASTNode psiElement, boolean isModified);

  @Override
  default boolean doEnter(Editor editor, PsiElement psiElement, boolean isModified) {
    ASTNode node = BasicJavaAstTreeUtil.toNode(psiElement);
    if (node == null) {
      return false;
    }
    return doEnter(editor, node, isModified);
  }
}

