package com.intellij.dupLocator.treeHash;

import com.intellij.dupLocator.DuplicatesProfile;
import com.intellij.dupLocator.DuplocateVisitor;
import com.intellij.dupLocator.DuplocatorSettings;
import com.intellij.dupLocator.ExternalizableDuplocatorState;
import com.intellij.dupLocator.util.DuplocatorUtil;
import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

public abstract class DuplicatesProfileBase extends DuplicatesProfile {
  @Override
  public @NotNull DuplocateVisitor createVisitor(@NotNull FragmentsCollector collector) {
    return new NodeSpecificHasherBase(DuplocatorSettings.getInstance(), collector, this);
  }

  public abstract int getNodeCost(@NotNull PsiElement element);

  public TokenSet getLiterals() {
    return TokenSet.EMPTY;
  }

  @Override
  public @NotNull ExternalizableDuplocatorState getDuplocatorState(@NotNull Language language) {
    return DuplocatorUtil.registerAndGetState(language);
  }

}
