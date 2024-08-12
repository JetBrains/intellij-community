// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.NodeSortKey;
import com.intellij.ide.projectView.NodeSortSettings;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;

import static com.intellij.openapi.util.text.StringUtil.naturalCompare;

public class GroupByTypeComparator implements Comparator<NodeDescriptor<?>> {
  private final Project project;
  private String myPaneId;
  private boolean myForceSortByType;

  public GroupByTypeComparator(@Nullable Project project, String paneId) {
    this.project = project;
    myPaneId = paneId;
  }

  public GroupByTypeComparator(boolean forceSortByType) {
    myForceSortByType = forceSortByType;
    this.project = null;
  }

  @Override
  public int compare(NodeDescriptor descriptor1, NodeDescriptor descriptor2) {
    descriptor1 = getUpdatedDescriptor(descriptor1);
    descriptor2 = getUpdatedDescriptor(descriptor2);

    if (descriptor1 instanceof ProjectViewNode<?> node1 && descriptor2 instanceof ProjectViewNode<?> node2) {

      NodeSortSettings settings = NodeSortSettings.of(isManualOrder(), getSortKey(), isFoldersAlwaysOnTop());
      int sortResult = node1.getSortOrder(settings).compareTo(node2.getSortOrder(settings));
      if (sortResult != 0) return sortResult;

      if (settings.isManualOrder()) {
        Comparable<?> key1 = node1.getManualOrderKey();
        Comparable<?> key2 = node2.getManualOrderKey();
        int result = compare(key1, key2);
        if (result != 0) return result;
      }

      if (settings.isFoldersAlwaysOnTop()) {
        int typeWeight1 = node1.getTypeSortWeight(settings.isSortByType());
        int typeWeight2 = node2.getTypeSortWeight(settings.isSortByType());
        if (typeWeight1 != 0 && typeWeight2 == 0) {
          return -1;
        }
        if (typeWeight1 == 0 && typeWeight2 != 0) {
          return 1;
        }
        if (typeWeight1 != 0 && typeWeight2 != typeWeight1) {
          return typeWeight1 - typeWeight2;
        }
      }

      switch (settings.getSortKey()) {
        case BY_NAME -> {
          final Comparable<?> sortKey1 = node1.getSortKey();
          final Comparable<?> sortKey2 = node2.getSortKey();
          if (sortKey1 != null && sortKey2 != null) {
            int result = compare(sortKey1, sortKey2);
            if (result != 0) return result;
          }
        }
        case BY_TYPE -> {
          final Comparable<?> typeSortKey1 = node1.getTypeSortKey();
          final Comparable<?> typeSortKey2 = node2.getTypeSortKey();
          int result = compare(typeSortKey1, typeSortKey2);
          if (result != 0) return result;
        }
        case BY_TIME_DESCENDING, BY_TIME_ASCENDING -> {
          final Comparable<?> timeSortKey1 = node1.getTimeSortKey();
          final Comparable<?> timeSortKey2 = node2.getTimeSortKey();
          int result = settings.getSortKey() == NodeSortKey.BY_TIME_ASCENDING
                       ? compare(timeSortKey1, timeSortKey2, false)
                       : compare(timeSortKey2, timeSortKey1, true);
          if (result != 0) return result;
        }
      }

      if (isAbbreviateQualifiedNames()) {
        String key1 = node1.getQualifiedNameSortKey();
        String key2 = node2.getQualifiedNameSortKey();
        if (key1 != null && key2 != null) {
          return naturalCompare(key1, key2);
        }
      }
    }
    if (descriptor1 == null) return -1;
    if (descriptor2 == null) return 1;
    return AlphaComparator.getInstance().compare(descriptor1, descriptor2);
  }

  private NodeDescriptor<?> getUpdatedDescriptor(NodeDescriptor<?> descriptor) {
    if (
      getSortKey() == NodeSortKey.BY_NAME &&
      descriptor instanceof ProjectViewNode<?> projectViewNode && projectViewNode.isSortByFirstChild()
    ) {
      Collection<? extends AbstractTreeNode<?>> children = projectViewNode.getChildren();
      if (!children.isEmpty()) {
        descriptor = children.iterator().next();
        descriptor.update();
      }
    }
    return descriptor;
  }

  protected @NotNull NodeSortKey getSortKey() {
    if (project == null) {
      return myForceSortByType ? NodeSortKey.BY_TYPE : NodeSortKey.BY_NAME;
    }
    return ProjectView.getInstance(project).getSortKey(myPaneId);
  }

  protected boolean isManualOrder() {
    if (project == null) {
      return true;
    }
    return ProjectView.getInstance(project).isManualOrder(myPaneId);
  }

  protected boolean isAbbreviateQualifiedNames() {
    return project != null && ProjectView.getInstance(project).isAbbreviatePackageNames(myPaneId);
  }

  protected boolean isFoldersAlwaysOnTop() {
    return project == null || ProjectView.getInstance(project).isFoldersAlwaysOnTop(myPaneId);
  }

  private static int compare(Comparable<?> key1, Comparable<?> key2) {
    return compare(key1, key2, true);
  }

  private static int compare(Comparable<?> key1, Comparable<?> key2, boolean nullsLast) {
    if (key1 == null && key2 == null) return 0;
    if (key1 == null) return nullsLast ? 1 : -1;
    if (key2 == null) return nullsLast ? -1 : 1;
    if (key1 instanceof String && key2 instanceof String) {
      return naturalCompare((String)key1, (String)key2);
    }

    try {
      //noinspection unchecked,rawtypes
      return ((Comparable)key1).compareTo(key2);
    }
    catch (ClassCastException ignored) {
      // if custom nodes provide comparable keys of different types,
      // let's try to compare class names instead to avoid broken trees
      return key1.getClass().getName().compareTo(key2.getClass().getName());
    }
  }
}
