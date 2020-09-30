// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.projectView.impl.AbstractUrl;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class AbstractUrlFavoriteAdapter extends AbstractUrl {
  @NotNull
  private final FavoriteNodeProvider myNodeProvider;

  public AbstractUrlFavoriteAdapter(String url, String moduleName, @NotNull FavoriteNodeProvider nodeProvider) {
    super(url, moduleName, nodeProvider.getFavoriteTypeId());
    myNodeProvider = nodeProvider;
  }

  @Override
  public Object[] createPath(Project project) {
    return myNodeProvider.createPathFromUrl(project, url, moduleName);
  }

  @Override
  protected AbstractUrl createUrl(String moduleName, String url) {
    return null;
  }

  @Override
  public AbstractUrl createUrlByElement(Object element) {
    return null;
  }

  @NotNull
  public FavoriteNodeProvider getNodeProvider() {
    return myNodeProvider;
  }
}
