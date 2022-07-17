// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.bookmark.Bookmark;
import com.intellij.ide.bookmark.BookmarksManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AbstractUrlFavoriteConverter {
  @Nullable Object createBookmarkContext(@NotNull Project project, @NotNull String url, @Nullable String moduleName);

  default @Nullable Bookmark createBookmark(@NotNull Project project, @NotNull String url, @Nullable String moduleName) {
    BookmarksManager manager = BookmarksManager.getInstance(project);
    if (manager == null) return null;
    Object context = createBookmarkContext(project, url, moduleName);
    if (context == null) return null;
    return manager.createBookmark(context);
  }
}
