// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.bookmark.Bookmark;
import com.intellij.ide.projectView.impl.AbstractUrl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


@Deprecated(forRemoval = true)
public class AbstractUrlFavoriteAdapter extends AbstractUrl {
  private static final Logger LOG = Logger.getInstance(AbstractUrlFavoriteAdapter.class);

  private final @NotNull FavoriteNodeProvider myNodeProvider;

  public AbstractUrlFavoriteAdapter(String url, String moduleName, @NotNull FavoriteNodeProvider nodeProvider) {
    super(url, moduleName, nodeProvider.getFavoriteTypeId());
    myNodeProvider = nodeProvider;
  }

  @Override
  public Object[] createPath(Project project) {
    return myNodeProvider.createPathFromUrl(project, url, moduleName);
  }

  @Nullable Bookmark createBookmark(@NotNull Project project) {
    if (myNodeProvider instanceof AbstractUrlFavoriteConverter converter) {
      var bookmark = converter.createBookmark(project, url, moduleName);
      if (bookmark != null) return bookmark;
      var id = myNodeProvider.getFavoriteTypeId();
      LOG.warn("cannot convert favorite: id=" + id + "; module=" + moduleName + "; url: " + url);
    }
    return null;
  }

  @Override
  protected AbstractUrl createUrl(String moduleName, String url) {
    return null;
  }

  @Override
  public AbstractUrl createUrlByElement(Object element) {
    return null;
  }

  public @NotNull FavoriteNodeProvider getNodeProvider() {
    return myNodeProvider;
  }
}
