// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.inspector;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * Allows passing additional information about Tree renderer to the UI Inspector.
 * <p>
 * Can be implemented by {@link javax.swing.tree.TreeCellRenderer}.
 *
 * @see UiInspectorContextProvider
 */
public interface UiInspectorTreeRendererContextProvider {
  @NotNull
  List<PropertyBean> getUiInspectorContext(@NotNull JTree tree, @Nullable Object value, int row);
}
