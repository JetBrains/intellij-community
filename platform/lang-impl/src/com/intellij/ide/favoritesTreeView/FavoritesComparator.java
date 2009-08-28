/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

/*
 * User: anna
 * Date: 05-Apr-2007
 */
package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.projectView.impl.nodes.LibraryGroupElement;
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElement;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPsiElementPointer;

import java.util.Comparator;

public class FavoritesComparator implements Comparator<NodeDescriptor> {
  private final FavoriteNodeProvider[] myNodeProviders;
  private final boolean mySortByType;

  public FavoritesComparator(final boolean sortByType, final Project project) {
    mySortByType = sortByType;
    myNodeProviders = Extensions.getExtensions(FavoriteNodeProvider.EP_NAME, project);
  }

  private int getWeight(NodeDescriptor descriptor) {
    FavoritesTreeNodeDescriptor favoritesTreeNodeDescriptor = (FavoritesTreeNodeDescriptor)descriptor;
    Object value = favoritesTreeNodeDescriptor.getElement().getValue();
    if (value instanceof SmartPsiElementPointer){
      value = ((SmartPsiElementPointer)value).getElement();
    }
    if (value instanceof ModuleGroup){
      return 0;
    }
    if (value instanceof Module){
      return 1;
    }
    if (value instanceof PsiDirectory){
      return 2;
    }
   
    if (value instanceof PsiFile){
      return 6;
    }
    if (value instanceof PsiElement){
      return 7;
    }
    if (value instanceof LibraryGroupElement){
      return 8;
    }
    if (value instanceof NamedLibraryElement){
      return 9;
    }
    for(FavoriteNodeProvider provider: myNodeProviders) {
      int weight = provider.getElementWeight(value, mySortByType);
      if (weight != -1) return weight;
    }
    return 9;
  }

  public int compare(NodeDescriptor nd1, NodeDescriptor nd2) {
    if (nd1 instanceof FavoritesTreeNodeDescriptor && nd2 instanceof FavoritesTreeNodeDescriptor){
      FavoritesTreeNodeDescriptor fd1 = (FavoritesTreeNodeDescriptor)nd1;
      FavoritesTreeNodeDescriptor fd2 = (FavoritesTreeNodeDescriptor)nd2;
      int weight1 = getWeight(fd1);
      int weight2 = getWeight(fd2);
      if (weight1 != weight2) {
        if (mySortByType) {
          if (weight1 < 10) {
            if (weight2 > 10) {//class kind
              weight2 = 3;
            }
          }
          else if (weight2 < 10) {
            if (weight1 > 10) {
              weight1 = 3;
            }
          }
        }
        return weight1 - weight2;
      }
      String s1 = fd1.toString();
      String s2 = fd2.toString();
      if (s1 == null) return s2 == null ? 0 : -1;
      if (s2 == null) return +1;
      if (!s1.equals(s2)) {
        return s1.compareToIgnoreCase(s2);
      }
      else {
        s1 = fd1.getLocation();
        s2 = fd2.getLocation();
        if (s1 == null) return s2 == null ? 0 : -1;
        if (s2 == null) return +1;
        return s1.compareToIgnoreCase(s2);
      }
    }
    return 0;
  }
}