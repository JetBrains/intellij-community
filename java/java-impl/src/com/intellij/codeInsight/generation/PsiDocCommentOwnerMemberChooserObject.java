// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.generation;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.render.RenderingUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class PsiDocCommentOwnerMemberChooserObject extends PsiElementMemberChooserObject {
  private final boolean myIsValid;
  public PsiDocCommentOwnerMemberChooserObject(@NotNull PsiDocCommentOwner owner, final @NlsSafe String text, Icon icon) {
    super(owner, text, icon);
    myIsValid = owner.isValid() && owner.isDeprecated();
  }

  @Override
  protected SimpleTextAttributes getTextAttributes(final JTree tree) {
    return new SimpleTextAttributes(myIsValid ? SimpleTextAttributes.STYLE_STRIKEOUT : SimpleTextAttributes.STYLE_PLAIN,
      RenderingUtil.getForeground(tree));
  }
}
