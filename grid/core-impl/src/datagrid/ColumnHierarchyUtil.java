package com.intellij.database.datagrid;

import com.intellij.util.containers.JBTreeTraverser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import static com.intellij.util.containers.TreeTraversal.LEAVES_DFS;
import static java.lang.Math.max;

public final class ColumnHierarchyUtil {
  public static @NotNull <Column> List<List<Column>> getPathsToLeafColumns(@NotNull JBTreeTraverser<Column> traverser) {
    List<List<Column>> paths = new ArrayList<>();

    traverseAndProcess(traverser, (stack, node) -> {
      if (traverser.children(node).isEmpty()) {
        paths.add(new ArrayList<>(stack));
      }
    });

    return paths;
  }

  private static <Column> void traverseAndProcess(
    @NotNull JBTreeTraverser<Column> traverser,
    @NotNull BiConsumer<List<Column>, Column> nodeProcessor
  ) {
    List<Column> stack = new ArrayList<>();

    try {
      for (Column node : traverser.preOrderDfsTraversal()) {
        while (!stack.isEmpty() && !traverser.children(stack.get(stack.size() - 1)).contains(node)) {
          stack.remove(stack.size() - 1);
        }

        stack.add(node);
        nodeProcessor.accept(stack, node);
      }
    } catch (BreakException ignored) {
      // Break out of the traversal loop gracefully
    }
  }

  private static class BreakException extends RuntimeException {}

  public static @NotNull <Column> List<Column> getPathToColumn(
    @NotNull JBTreeTraverser<Column> traverser,
    @NotNull Column targetColumn
  ) {
    List<Column> path = new ArrayList<>();

    traverseAndProcess(traverser, (stack, node) -> {
      if (node.equals(targetColumn)) {
        path.addAll(stack);
        throw new BreakException();
      }
    });

    return path;
  }

  public static <Column> int getMaxDepth(@NotNull JBTreeTraverser<Column> traverser) {
    return getMaxDepth(traverser, null);
  }

  public static <Column> int getMaxDepth(@NotNull JBTreeTraverser<Column> traverser,
                                         @Nullable Predicate<Column> shouldSkip) {
    int[] maxDepth = {0};

    traverseAndProcess(traverser, (stack, node) -> {
      if (shouldSkip != null && shouldSkip.test(node)) return;
      maxDepth[0] = max(maxDepth[0], stack.size());
    });

    return maxDepth[0];
  }

  public static @NotNull <Column> List<Column> getAllLeafNodesInSubtree(@NotNull JBTreeTraverser<Column> traverser, @NotNull Column node) {
    return traverser.withRoot(node).traverse(LEAVES_DFS).toList();
  }
}
