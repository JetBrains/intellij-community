package org.intellij.lang.regexp;

import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RegExpBraceMatcher implements PairedBraceMatcher {
  public BracePair[] getPairs() {
    return new BracePair[]{new BracePair(RegExpTT.GROUP_BEGIN, RegExpTT.GROUP_END, true),
      new BracePair(RegExpTT.SET_OPTIONS, RegExpTT.GROUP_END, true), new BracePair(RegExpTT.NON_CAPT_GROUP, RegExpTT.GROUP_END, true),
      new BracePair(RegExpTT.POS_LOOKAHEAD, RegExpTT.GROUP_END, true), new BracePair(RegExpTT.NEG_LOOKAHEAD, RegExpTT.GROUP_END, true),
      new BracePair(RegExpTT.POS_LOOKBEHIND, RegExpTT.GROUP_END, true), new BracePair(RegExpTT.NEG_LOOKBEHIND, RegExpTT.GROUP_END, true),
      new BracePair(RegExpTT.CLASS_BEGIN, RegExpTT.CLASS_END, false), new BracePair(RegExpTT.LBRACE, RegExpTT.RBRACE, false),
      new BracePair(RegExpTT.QUOTE_BEGIN, RegExpTT.QUOTE_END, false),};
  }

  public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType lbraceType, @Nullable IElementType contextType) {
    return false;
  }

  public int getCodeConstructStart(PsiFile file, int openingBraceOffset) {
    return openingBraceOffset;
  }
}
