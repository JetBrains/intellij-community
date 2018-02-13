package com.intellij.json;

import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class JsonBraceMatcher implements PairedBraceMatcher {
  private static final BracePair[] PAIRS = {
    new BracePair(JsonElementTypes.L_BRACKET, JsonElementTypes.R_BRACKET, true),
    new BracePair(JsonElementTypes.L_CURLY, JsonElementTypes.R_CURLY, true)
  };

  @NotNull
  @Override
  public BracePair[] getPairs() {
    return PAIRS;
  }

  @Override
  public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType lbraceType, @Nullable IElementType contextType) {
    return true;
  }

  @Override
  public int getCodeConstructStart(PsiFile file, int openingBraceOffset) {
    return openingBraceOffset;
  }
}
