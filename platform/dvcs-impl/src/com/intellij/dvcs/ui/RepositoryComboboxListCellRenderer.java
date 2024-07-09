// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.ui;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.repo.Repository;
import com.intellij.ui.SimpleListCellRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Common {@link ListCellRenderer} do be used in {@link JComboBox} displaying {@link Repository repositories}.
 * We don't want to use {@link Repository#toString()} since it is not the best way to display the repository in the UI.
 *
 * @author Kirill Likhodedov
 */
public final class RepositoryComboboxListCellRenderer extends SimpleListCellRenderer<Repository> {
  @Override
  public void customize(@NotNull JList<? extends Repository> list, Repository value, int index, boolean selected, boolean hasFocus) {
    setText(DvcsUtil.getShortRepositoryName(value));
  }
}
