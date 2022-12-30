// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmarks;

import com.intellij.ide.bookmark.BookmarkType;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.*;

@Deprecated(forRemoval = true)
public class BookmarksFavoriteListProvider {
  @VisibleForTesting
  public static final Icon BOOKMARK = BookmarkType.DEFAULT.getIcon();
}
