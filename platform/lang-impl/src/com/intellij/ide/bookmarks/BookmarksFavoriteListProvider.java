// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmarks;

import com.intellij.ide.bookmark.BookmarkType;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.*;

@Deprecated(forRemoval = true)
public final class BookmarksFavoriteListProvider {
  @VisibleForTesting
  public static final Icon BOOKMARK = BookmarkType.DEFAULT.getIcon();
}
