/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

/*
 * User: anna
 * Date: 05-Apr-2007
 */
package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.GroupByTypeComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;

public class FavoritesComparator extends GroupByTypeComparator {

  public FavoritesComparator(ProjectView projectView, String paneId) {
    super(projectView, paneId);
  }

  public int compare(NodeDescriptor nd1, NodeDescriptor nd2) {
    if (nd1 instanceof FavoritesTreeNodeDescriptor && nd2 instanceof FavoritesTreeNodeDescriptor){
      FavoritesTreeNodeDescriptor fd1 = (FavoritesTreeNodeDescriptor)nd1;
      FavoritesTreeNodeDescriptor fd2 = (FavoritesTreeNodeDescriptor)nd2;
      return super.compare(fd1.getElement(), fd2.getElement());
    }
    return 0;
  }
}