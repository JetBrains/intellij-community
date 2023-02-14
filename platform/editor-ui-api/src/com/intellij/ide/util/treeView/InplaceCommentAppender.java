// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.treeView;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

/**
 * An interface to handle appending inplace comments to a tree node.
 * <p>
 *   This interface is needed because there are two ways to handle inplace comments.
 *   The legacy approach appends them directly to the cell renderer (a {@code SimpleColoredComponent}),
 *   while the modern approach is to append them directly to the presentation (a {@link com.intellij.ide.projectView.PresentationData}).
 *   They do not share a common interface, so this interface serves an adapter.
 * </p>
 */
public interface InplaceCommentAppender {

  /**
   * Appends the given comment with the given attributes.
   * @param text the comment to append
   * @param attributes the comment's attributes
   */
  void append(@NotNull @NlsSafe String text, @NotNull SimpleTextAttributes attributes);

}
