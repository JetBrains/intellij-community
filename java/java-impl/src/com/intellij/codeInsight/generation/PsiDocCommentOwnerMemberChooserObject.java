// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.generation;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.render.RenderingUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class PsiDocCommentOwnerMemberChooserObject extends PsiElementMemberChooserObject {
  public PsiDocCommentOwnerMemberChooserObject(@NotNull PsiDocCommentOwner owner, final @NlsSafe String text, Icon icon) {
    super(owner, text, icon);
  }

  public PsiDocCommentOwner getPsiDocCommentOwner() {
    return (PsiDocCommentOwner)getPsiElement();
  }

  @Override
  protected SimpleTextAttributes getTextAttributes(final JTree tree) {
    PsiDocCommentOwner owner = getPsiDocCommentOwner();
    return new SimpleTextAttributes(
      owner.isValid() && owner.isDeprecated() ? SimpleTextAttributes.STYLE_STRIKEOUT : SimpleTextAttributes.STYLE_PLAIN,
      RenderingUtil.getForeground(tree));
  }
}
