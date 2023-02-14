package com.intellij.dupLocator.treeHash;

import com.intellij.dupLocator.*;
import com.intellij.dupLocator.util.DuplocatorUtil;
import com.intellij.dupLocator.util.PsiFragment;
import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

public abstract class DuplicatesProfileBase extends DuplicatesProfile {
  @NotNull
  @Override
  public DuplocateVisitor createVisitor(@NotNull FragmentsCollector collector) {
    return new NodeSpecificHasherBase(DuplocatorSettings.getInstance(), collector, this);
  }

  public abstract int getNodeCost(@NotNull PsiElement element);

  public TokenSet getLiterals() {
    return TokenSet.EMPTY;
  }

  @Override
  @NotNull
  public ExternalizableDuplocatorState getDuplocatorState(@NotNull Language language) {
    return DuplocatorUtil.registerAndGetState(language);
  }

}
