// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface BookmarksListener {
  Topic<BookmarksListener> TOPIC = Topic.create("Bookmarks", BookmarksListener.class);

  default void groupsSorted() {
  }

  default void groupAdded(@NotNull BookmarkGroup group) {
  }

  default void groupRemoved(@NotNull BookmarkGroup group) {
  }

  default void groupRenamed(@NotNull BookmarkGroup group) {
  }

  default void bookmarksSorted(@NotNull BookmarkGroup group) {
  }

  default void bookmarkAdded(@NotNull BookmarkGroup group, @NotNull Bookmark bookmark) {
  }

  default void bookmarkRemoved(@NotNull BookmarkGroup group, @NotNull Bookmark bookmark) {
  }

  default void bookmarkChanged(@NotNull BookmarkGroup group, @NotNull Bookmark bookmark) {
  }

  default void bookmarkTypeChanged(@NotNull Bookmark bookmark) {
  }

  default void defaultGroupChanged(@Nullable BookmarkGroup oldGroup, @Nullable BookmarkGroup newGroup) {
  }

  default void structureChanged(@Nullable Object node) {
  }
}
