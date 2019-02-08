package com.intellij.dupLocator.treeHash;

import com.intellij.dupLocator.NodeSpecificHasher;
import com.intellij.dupLocator.util.PsiFragment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafElement;

import java.util.List;

public class TreeHashingUtils {
  public static TreeHashResult hashCodeBlockForIndexing(AbstractTreeHasher treeHasher, FragmentsCollector callBack,
                                                           List<? extends PsiElement> statements,
                                                           PsiFragment upper,
                                                           NodeSpecificHasher hasher) {
    final int statementsSize = statements.size();

    if (statementsSize > 0) {
      final PsiFragment fragment = treeHasher.buildFragment(hasher, statements, 0, statementsSize - 1);
      fragment.setParent(upper);
      int cost = 0;
      int hash = 0;
      for (PsiElement statement : statements) {
        final TreeHashResult res = treeHasher.hash(statement, null, hasher);
        hash = hash* 31 + res.getHash();
        cost += res.getCost();
      }

      TreeHashResult result = new TreeHashResult(hash, cost, treeHasher.buildFragment(hasher, statements, 0, statementsSize - 1));
      if (callBack != null && statementsSize > 1) callBack.add(hash, cost, fragment);
      return result;
    }
    return new TreeHashResult(1, 0, treeHasher.buildFragment(hasher, statements, 0, statementsSize - 1));
  }
  static TreeHashResult computeElementHashForIndexing(AbstractTreeHasher base,
                                                      FragmentsCollector callBack,
                                                      PsiElement root,
                                                      PsiFragment upper,
                                                      NodeSpecificHasher hasher
  ) {
    final List<PsiElement> children = hasher.getNodeChildren(root);
    final PsiFragment fragment = base.buildFragment(hasher, root, base.getCost(root));

    if (upper != null) {
      fragment.setParent(upper);
    }

    final int size = children.size();
    if (size == 0 && !(root instanceof LeafElement)) {
      // contains only whitespaces and other unmeaning children
      return new TreeHashResult(0, hasher.getNodeCost(root), fragment);
    }

    final int discardCost = base.getDiscardCost(root);
    int c = hasher.getNodeCost(root);
    int h = hasher.getNodeHash(root);

    for (int i = 0; i < size; i++) {
      PsiElement child = children.get(i);
      final TreeHashResult res = base.hash(child, fragment, hasher);
      int childCost = res.getCost();
      c += childCost;
      if (childCost > discardCost || !base.ignoreChildHash(child)) {
        h += res.getHash();
      }
    }

    if (base.shouldAnonymize(root, hasher)) {
      h = 0;
    }

    if (callBack != null) {
      callBack.add(h, c, fragment);
    }

    return new TreeHashResult(h, c, fragment);
  }
}
