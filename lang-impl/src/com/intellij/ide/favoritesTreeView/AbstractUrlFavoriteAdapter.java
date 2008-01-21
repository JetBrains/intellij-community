/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.projectView.impl.AbstractUrl;
import com.intellij.openapi.project.Project;

/**
 * @author yole
 */
public class AbstractUrlFavoriteAdapter extends AbstractUrl {
  private final FavoriteNodeProvider myNodeProvider;

  public AbstractUrlFavoriteAdapter(final String url, final String moduleName, final FavoriteNodeProvider nodeProvider) {
    super(url, moduleName, nodeProvider.getFavoriteTypeId());
    myNodeProvider = nodeProvider;
  }

  public Object[] createPath(Project project) {
    return myNodeProvider.createPathFromUrl(project, url, moduleName);
  }

  protected AbstractUrl createUrl(String moduleName, String url) {
    return null;
  }

  public AbstractUrl createUrlByElement(Object element) {
    return null;
  }
}
