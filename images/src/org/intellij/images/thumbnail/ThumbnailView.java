/*
 * Copyright 2004-2005 Alexey Efimov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.images.thumbnail;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.images.search.TagFilter;
import org.intellij.images.thumbnail.actions.ThemeFilter;
import org.intellij.images.ui.ImageComponentDecorator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thumbnail thumbnail is a component with thumbnails for a set of {@link VirtualFile}.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public interface ThumbnailView extends Disposable, ImageComponentDecorator {
  DataKey<ThumbnailView> DATA_KEY = DataKey.create(ThumbnailView.class.getName());

  @NonNls String TOOLWINDOW_ID = "Thumbnails";

  @NotNull
  Project getProject();

  /**
   * Add virtual files to view
   *
   * @param root Root
   */
  void setRoot(@NotNull VirtualFile root);

  /**
   * Return current root
   *
   * @return Current root
   */
  VirtualFile getRoot();

  boolean isRecursive();

  void setRecursive(boolean recursive);

  void setSelected(@NotNull VirtualFile file);

  boolean isSelected(@NotNull VirtualFile file);

  VirtualFile @NotNull [] getSelection();

  /**
   * Scroll to selection. If ToolWindow is not active, then
   * it will perform activation before scroll.
   */
  void scrollToSelection();

  void setVisible(boolean visible);

  boolean isVisible();

  void activate();

  void setFilter(ThemeFilter filter);

  /**
   * null means all files accepted
   */
  @Nullable
  ThemeFilter getFilter();

  void setTagFilters(TagFilter[] filter);

  /**
   * null means all files accepted
   */
  TagFilter @Nullable [] getTagFilters();

  /**
   * update UI: preview visibility, etc
   */
  void refresh();
}
