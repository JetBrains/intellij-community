// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.psi.PsiElement;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.HintUpdateSupply;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

/**
 * @deprecated use HintUpdateSupply directly
 * @see HintUpdateSupply
 */
@Deprecated(forRemoval = true)
public abstract class JBListWithHintProvider extends JBList {
  {
    HintUpdateSupply.installHintUpdateSupply(this, o -> getPsiElementForHint(o));
  }

  public JBListWithHintProvider() {
  }

  public JBListWithHintProvider(ListModel dataModel) {
    super(dataModel);
  }

  public JBListWithHintProvider(Object... listData) {
    super(listData);
  }

  public JBListWithHintProvider(Collection items) {
    super(items);
  }

  protected abstract @Nullable PsiElement getPsiElementForHint(final Object selectedValue);
}
