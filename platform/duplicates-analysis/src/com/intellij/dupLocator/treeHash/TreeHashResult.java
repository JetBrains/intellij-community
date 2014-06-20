package com.intellij.dupLocator.treeHash;

import com.intellij.dupLocator.util.PsiFragment;

/**
* @author oleg
*/
public class TreeHashResult {
  int myHash;
  int myCost;
  PsiFragment myFragment;

  public TreeHashResult(final int hash, final int cost, final PsiFragment fragment) {
    myHash = hash;
    myCost = cost;
    myFragment = fragment;
  }

  public int getHash() {
    return myHash;
  }

  public int getCost() {
    return myCost;
  }

  public PsiFragment getFragment() {
    return myFragment;
  }
}
