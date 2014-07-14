package com.intellij.dupLocator;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: Mar 26, 2004
 * Time: 3:48:37 PM
 * To change this template use File | Settings | File Templates.
 */
public class TreeComparator {
  private TreeComparator() {
  }

  public static boolean areEqual(@NotNull PsiElement x, @NotNull PsiElement y, NodeSpecificHasher hasher, int discardCost) {
    final int costX = hasher.getNodeCost(x);
    final int costY = hasher.getNodeCost(y);
    if (costX == -1 || costY == -1) return false;
    if (costX < discardCost || costY < discardCost) {
      return true;
    }

    if (hasher.areNodesEqual(x, y)) {
      if (!hasher.checkDeep(x, y)) return true;
      List<PsiElement> xSons = hasher.getNodeChildren(x);
      List<PsiElement> ySons = hasher.getNodeChildren(y);

      if (xSons.size() == ySons.size()) {
        for (int i = 0; i < ySons.size(); i++) {
          if (!areEqual(xSons.get(i), ySons.get(i), hasher, discardCost)) {
            return false;
          }
        }

        return true;
      }
    }

    return false;
  }
}
