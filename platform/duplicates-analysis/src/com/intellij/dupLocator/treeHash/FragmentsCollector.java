package com.intellij.dupLocator.treeHash;

import com.intellij.dupLocator.util.PsiFragment;
import org.jetbrains.annotations.Nullable;

public interface FragmentsCollector {
  void add(int hash, int cost, @Nullable PsiFragment frag);
}
