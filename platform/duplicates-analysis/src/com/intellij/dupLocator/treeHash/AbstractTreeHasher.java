package com.intellij.dupLocator.treeHash;

import com.intellij.dupLocator.NodeSpecificHasher;
import com.intellij.dupLocator.TreeHasher;
import com.intellij.dupLocator.util.PsiFragment;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: db, oleg
 * Date: Mar 26, 2004
 * Time: 4:44:13 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class AbstractTreeHasher implements TreeHasher {

  protected final FragmentsCollector myCallBack;

  public AbstractTreeHasher(FragmentsCollector cb) {
    myCallBack = cb;
  }

  public final void hash(@NotNull final PsiElement root, @NotNull final NodeSpecificHasher hasher) {
    hash(root, null, hasher);
  }

  protected abstract TreeHashResult hash(@NotNull PsiElement root, PsiFragment upper, @NotNull NodeSpecificHasher hasher);

  /**
   * Computes element hash using children hashes.
   * Creates only single PsiFragment.
   */
  protected TreeHashResult computeElementHash(@NotNull final PsiElement root, final PsiFragment upper, final NodeSpecificHasher hasher) {
    ProgressManager.checkCanceled();
    final List<PsiElement> children = hasher.getNodeChildren(root);
    final int size = children.size();
    final int[] childHashes = new int[size];
    final int[] childCosts = new int[size];

    final PsiFragment fragment = new TreePsiFragment(hasher, root, getCost(root));

    if (upper != null) {
      fragment.setParent(upper);
    }

    if (size == 0 && !(root instanceof LeafElement)) {
      return new TreeHashResult(hasher.getNodeHash(root), hasher.getNodeCost(root), fragment);
    }

    for (int i = 0; i < size; i++) {
      final TreeHashResult res = hash(children.get(i), fragment, hasher);
      childHashes[i] = res.getHash();
      childCosts[i] = res.getCost();
    }

    final int c = hasher.getNodeCost(root) + AbstractTreeHasher.vector(childCosts);
    final int h1 = hasher.getNodeHash(root);

    final int discardCost = getDiscardCost(root);

    for (int i = 0; i < size; i++) {
      if (childCosts[i] <= discardCost && ignoreChildHash(children.get(i))) {
        childHashes[i] = 0;
      }
    }
    final int h = h1 + AbstractTreeHasher.vector(childHashes);

    if (myCallBack != null) {
      myCallBack.add(h, c, fragment);
    }

    return new TreeHashResult(h, c, fragment);
  }

  protected abstract int getDiscardCost(PsiElement root);

  protected boolean ignoreChildHash(PsiElement element) {
    return false;
  }

  protected TreeHashResult hashCodeBlock(final List<? extends PsiElement> statements,
                                         final PsiFragment upper,
                                         final NodeSpecificHasher hasher) {
    return hashCodeBlock(statements, upper, hasher, false);
  }

  /**
   * Creates PsiFragments using given statements with their hashes
   */
  protected TreeHashResult hashCodeBlock(final List<? extends PsiElement> statements,
                                         final PsiFragment upper,
                                         final NodeSpecificHasher hasher,
                                         boolean forceHash) {
    final int statementsSize = statements.size();
    if (statementsSize == 1) {
      return hash(statements.get(0), upper, hasher);
    }
    if (statementsSize > 0) {
      // Here we compute all the possible code fragments using statements
      if (statementsSize < 20 || forceHash) {   //todo should be configurable
        final PsiFragment[] frags = new PsiFragment[statementsSize];

        final PsiFragment fragment = new TreePsiFragment(hasher, statements, 0, statementsSize - 1);
        fragment.setParent(upper);

        // Fill all the statements costs and hashes
        final int[] hashes = new int[statementsSize];
        final int[] costs = new int[statementsSize];
        for (int i = 0; i < statementsSize; i++) {
          final TreeHashResult res = hash(statements.get(i), null, hasher);
          hashes[i] = res.getHash();
          costs[i] = res.getCost();
          frags[i] = res.getFragment();
        }

        if (myCallBack != null) {
          final PsiFragment[] parents = new PsiFragment[statementsSize]; //parent(end) = [beg, end]
          for (int beg = 0; beg < statementsSize; beg++) {
            int hash = 0;
            int cost = 0;
            for (int end = beg; end < statementsSize && end - beg < 20; end++) {
              hash = 31 * hash + hashes[end];
              cost += costs[end];
              final PsiFragment curr =
                beg == end
                ? frags[beg]
                : beg == 0 && end == statementsSize - 1 ? fragment : new TreePsiFragment(hasher, statements, beg, end);
              if (beg > 0) {
                curr.setParent(parents[end]); //[beg, end].setParent([beg - 1, end])
              }
              parents[end] = curr;
              if (end > beg) {
                parents[end - 1].setParent(curr);//[beg, end - 1].setParent([beg, end])
              }
              myCallBack.add(hash, cost, curr);
            }
          }
        }
        return new TreeHashResult(vector(hashes, 31), vector(costs), fragment);
      }
    }
    return new TreeHashResult(1, 0, new TreePsiFragment(hasher, statements, 0, statementsSize - 1));
  }

  protected int getCost(final PsiElement root){
    return 0;
  }

  public static int vector(int[] args) {
    return vector(args, 1);
  }

  public static int vector(int[] args, int mult) {
    int sum = 0;
    for (int arg : args) {
      sum = mult * sum + arg;
    }
    return sum;
  }
}
