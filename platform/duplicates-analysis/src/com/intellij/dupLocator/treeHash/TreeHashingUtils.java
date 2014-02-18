package com.intellij.dupLocator.treeHash;

import com.intellij.dupLocator.NodeSpecificHasher;
import com.intellij.dupLocator.util.PsiFragment;
import com.intellij.psi.PsiElement;

import java.util.List;

/**
 * Created by Maxim.Mossienko on 2/17/14.
 */
public class TreeHashingUtils {
  protected static TreeHashResult hashCodeBlockForIndexing(AbstractTreeHasher treeHasher, FragmentsCollector callBack,
                                                           List<? extends PsiElement> statements,
                                                           PsiFragment upper,
                                                           NodeSpecificHasher hasher) {
    final int statementsSize = statements.size();

    if (statementsSize > 0) {
      final PsiFragment fragment = new TreePsiFragment(hasher, statements, 0, statementsSize - 1);
      fragment.setParent(upper);
      int cost = 0;
      int hash = 0;
      for (PsiElement statement : statements) {
        final TreeHashResult res = treeHasher.hash(statement, null, hasher);
        hash = hash* 31 + res.getHash();
        cost += res.getCost();
      }

      TreeHashResult result = new TreeHashResult(hash, cost, new TreePsiFragment(hasher, statements, 0, statementsSize - 1));
      if (callBack != null && statementsSize > 1) callBack.add(hash, cost, fragment);
      return result;
    }
    return new TreeHashResult(1, 0, new TreePsiFragment(hasher, statements, 0, statementsSize - 1));
  }
}
