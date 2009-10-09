package com.intellij.codeInsight.highlighting;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface NontrivialBraceMatcher extends BraceMatcher {
  @NotNull
  List<IElementType> getOppositeBraceTokenTypes(@NotNull IElementType type);
}