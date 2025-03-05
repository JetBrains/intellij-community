package com.intellij.database.datagrid;

import com.intellij.database.datagrid.HierarchicalColumnsDataGridModel.HierarchicalGridColumn;
import com.intellij.util.containers.JBTreeTraverser;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public class HierarchicalReader {
  private final List<HierarchicalGridColumn> myRoots;

  private List<List<HierarchicalGridColumn>> myCachedPathToLeafs;

  private List<HierarchicalGridColumn> myCachedLeafs;

  private final Object2BooleanMap<int[]> myValidPathsCache;

  private int myDepthOfHierarchy = -1;

  HierarchicalReader(@NotNull List<HierarchicalGridColumn> roots) {
    myRoots = roots;
    myValidPathsCache = new Object2BooleanOpenHashMap<>();
  }

  public @NotNull JBTreeTraverser<HierarchicalGridColumn> hierarchy() {
    return JBTreeTraverser.from(c -> c.getChildren());
  }

  public boolean isValidPath(int[] path) {
    if (!myValidPathsCache.containsKey(path)) {
      List<HierarchicalGridColumn> leafs = getLeafs();
      boolean found = false;
      for (HierarchicalGridColumn leaf : leafs) {
        int[] pathFromRoot = leaf.getPathFromRoot();
        if (arrayContains(pathFromRoot, path)) {
          found = true;
          break;
        }
      }
      myValidPathsCache.put(path, found);
    }

    return myValidPathsCache.getBoolean(path);
  }

  private static boolean arrayContains(int[] arr1, int[] arr2) {
    if (arr2.length == 0 || arr1.length < arr2.length) {
      return false;
    }

    for (int i = 0; i <= arr1.length - arr2.length; i++) {
      boolean found = true;
      for (int j = 0; j < arr2.length; j++) {
        if (arr1[i + j] != arr2[j]) {
          found = false;
          break;
        }
      }
      if (found) {
        return true;
      }
    }
    return false;
  }

  private List<List<HierarchicalGridColumn>> getPathsToLeafs() {
    if (myCachedPathToLeafs == null) {
      myCachedPathToLeafs = ColumnHierarchyUtil.getPathsToLeafColumns(
        hierarchy().withRoots(myRoots)
      );
    }

    return myCachedPathToLeafs;
  }

  public int getDepthOfHierarchy() {
    if (myDepthOfHierarchy == -1) {
      myDepthOfHierarchy = ColumnHierarchyUtil.getMaxDepth(hierarchy().withRoots(myRoots));
    }

    return myDepthOfHierarchy;
  }

  public void updateDepthOfHierarchy(Predicate<HierarchicalGridColumn> shouldSkip) {
    myDepthOfHierarchy = ColumnHierarchyUtil.getMaxDepth(hierarchy().withRoots(myRoots), shouldSkip);
  }

  public int getLeafColumnsCount() {
    return getPathsToLeafs().size();
  }

  public @NotNull List<HierarchicalGridColumn> getLeafs() {
    if (myCachedLeafs == null) {
      List<List<HierarchicalGridColumn>> paths = getPathsToLeafs();
      myCachedLeafs = paths.stream()
        .filter(path -> !path.isEmpty())
        .map(path -> path.get(path.size() - 1))
        .toList();
    }

    return myCachedLeafs;
  }

  public int @NotNull [] getColumnPath(int column) {
    if (column < 0) return new int[] {-1};

    List<HierarchicalGridColumn> leafs = getLeafs();

    if (column >= leafs.size()) return new int[] {-1};

    return leafs.get(column).getPathFromRoot();
  }

  public @Nullable HierarchicalGridColumn getAncestorAtDepth(@NotNull HierarchicalGridColumn column, int depth) {
    int[] pathToColumn = column.getPathFromRoot();
    assert pathToColumn.length > 0; // should never happen
    if (depth < 0 || depth >= pathToColumn.length) return null;
    int rootIdx = pathToColumn[0];

    HierarchicalGridColumn ancestor = getNodeByIndex(myRoots, rootIdx);
    for (int curDepth = 1; curDepth <= depth && ancestor != null; ++curDepth) {
      ancestor = getNodeByIndex(ancestor.getChildren(), pathToColumn[curDepth]);
    }

    return ancestor;
  }

  private @Nullable HierarchicalGridColumn getNodeByIndex(List<HierarchicalGridColumn> nodes, int index) {
    if (index < 0 || index >= nodes.size()) return null;
    return nodes.get(index);
  }

  public @NotNull List<HierarchicalGridColumn> getAllLeafNodesInSubtree(@NotNull HierarchicalGridColumn column) {
    return ColumnHierarchyUtil.getAllLeafNodesInSubtree(hierarchy().withRoots(myRoots), column);
  }

  public @NotNull List<HierarchicalGridColumn> getSiblings(@NotNull HierarchicalGridColumn column) {
    if (column.getPathFromRoot().length == 1) return myRoots;
    HierarchicalGridColumn parent = column.getParent();
    return parent == null ? Collections.emptyList() : parent.getChildren();
  }
}
