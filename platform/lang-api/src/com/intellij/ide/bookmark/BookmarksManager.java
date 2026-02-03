// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface BookmarksManager {
  static @Nullable BookmarksManager getInstance(@Nullable Project project) {
    return project == null || project.isDisposed() ? null : project.getService(BookmarksManager.class);
  }

  @ApiStatus.Experimental
  Key<Boolean> ALLOWED = Key.create("allows bookmarks creation in the context component");

  @Nullable Bookmark createBookmark(@Nullable Object context);

  @NotNull List<Bookmark> getBookmarks();

  /**
   * @return default bookmark group
   */
  @Nullable BookmarkGroup getDefaultGroup();

  @Nullable BookmarkGroup getGroup(@NotNull String name);

  /**
   * @return list of bookmark groups
   */
  @NotNull List<BookmarkGroup> getGroups();

  /**
   * @return list of groups containing the specified bookmark
   */
  @NotNull @Unmodifiable List<BookmarkGroup> getGroups(@NotNull Bookmark bookmark);

  @Nullable BookmarkGroup addGroup(@NotNull String name, boolean isDefault);

  @Nullable Bookmark getBookmark(@NotNull BookmarkType type);

  @NotNull @Unmodifiable
  Set<BookmarkType> getAssignedTypes();

  /**
   * @return a bookmark type or {@code null} if the bookmark is not set
   */
  @Nullable BookmarkType getType(@NotNull Bookmark bookmark);

  /**
   * Changes type of the bookmark contained // TODO: add/remove?
   */
  void setType(@NotNull Bookmark bookmark, @NotNull BookmarkType type);

  void toggle(@NotNull Bookmark bookmark, @NotNull BookmarkType type);

  void add(@NotNull Bookmark bookmark, @NotNull BookmarkType type);

  void remove(@NotNull Bookmark bookmark);

  void remove();

  void update(@NotNull Map<@NotNull Bookmark, @Nullable Bookmark> map);
}
