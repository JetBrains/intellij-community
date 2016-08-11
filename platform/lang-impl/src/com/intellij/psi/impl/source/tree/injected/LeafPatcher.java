/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.psi.impl.source.tree.injected;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.tree.ForeignLeafPsiElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.RecursiveTreeElementWalkingVisitor;
import gnu.trove.THashMap;

import java.util.List;
import java.util.Map;

/**
 * @author cdr
*/
class LeafPatcher extends RecursiveTreeElementWalkingVisitor {
  private LeafElement prevElement;
  private String prevElementTail;
  private int shredNo;
  private String hostText;
  private TextRange rangeInHost;
  private final Place myShreds;
  private final List<LiteralTextEscaper<? extends PsiLanguageInjectionHost>> myEscapers;
  final Map<LeafElement, String> newTexts = new THashMap<>();
  final StringBuilder catLeafs = new StringBuilder();

  LeafPatcher(Place shreds, List<LiteralTextEscaper<? extends PsiLanguageInjectionHost>> escapers) {
    myShreds = shreds;
    myEscapers = escapers;
  }

  @Override
  public void visitLeaf(LeafElement leaf) {
    String leafText = leaf instanceof ForeignLeafPsiElement ? "" : leaf.getText();
    catLeafs.append(leafText);
    final TextRange leafRange = leaf.getTextRange();

    StringBuilder leafEncodedText = constructTextFromHostPSI(leafRange.getStartOffset(), leafRange.getEndOffset());

    if (leaf.getElementType() == TokenType.WHITE_SPACE && prevElementTail != null) {
      // optimization: put all garbage into whitespace
      leafEncodedText.insert(0, prevElementTail);
      newTexts.remove(prevElement);
      storeUnescapedTextFor(prevElement, null);
    }
    if (!Comparing.equal(leafText, leafEncodedText)) {
      newTexts.put(leaf, leafEncodedText.toString());
      storeUnescapedTextFor(leaf, leafText);
    }
    prevElementTail = StringUtil.startsWith(leafEncodedText, leafText) && leafEncodedText.length() != leafText.length() ?
                      leafEncodedText.substring(leafText.length()) : null;
    prevElement = leaf;
  }

  private StringBuilder constructTextFromHostPSI(int startOffset, int endOffset) {
    boolean firstTimer = false;
    PsiLanguageInjectionHost.Shred current = myShreds.get(shredNo);
    if (hostText == null) {
      hostText = current.getHost().getText();
      rangeInHost = current.getRangeInsideHost();
      firstTimer = true;
    }

    StringBuilder text = new StringBuilder(endOffset-startOffset);
    while (startOffset < endOffset) {
      TextRange shredRange = current.getRange();
      String prefix = current.getPrefix();
      if (startOffset >= shredRange.getEndOffset()) {
        current = myShreds.get(++shredNo);
        hostText = current.getHost().getText();
        rangeInHost = current.getRangeInsideHost();
        firstTimer = true;
        continue;
      }
      assert startOffset >= shredRange.getStartOffset();
      if (startOffset - shredRange.getStartOffset() < prefix.length()) {
        // inside prefix
        TextRange rangeInPrefix = new TextRange(startOffset - shredRange.getStartOffset(), Math.min(prefix.length(), endOffset - shredRange.getStartOffset()));
        text.append(prefix, rangeInPrefix.getStartOffset(), rangeInPrefix.getEndOffset());
        startOffset += rangeInPrefix.getLength();
        continue;
      }

      String suffix = current.getSuffix();
      if (startOffset < shredRange.getEndOffset() - suffix.length()) {
        // inside host body, cut out from the host text
        int startOffsetInHost = myEscapers.get(shredNo).getOffsetInHost(
          startOffset - shredRange.getStartOffset() - prefix.length(), rangeInHost);
        int endOffsetCut = Math.min(endOffset, shredRange.getEndOffset() - suffix.length());
        int endOffsetInHost = myEscapers.get(shredNo).getOffsetInHost(
          endOffsetCut - shredRange.getStartOffset() - prefix.length(), rangeInHost);
        if (endOffsetInHost != -1) {
          if (firstTimer ) text.append(hostText, rangeInHost.getStartOffset(), startOffsetInHost);
          text.append(hostText, startOffsetInHost, endOffsetInHost);
          startOffset = endOffsetCut;
          // todo what about lastTimer?
          continue;
        }
      }

      // inside suffix
      TextRange rangeInSuffix = new TextRange(suffix.length() - shredRange.getEndOffset() + startOffset, Math.min(suffix.length(), endOffset + suffix.length() - shredRange.getEndOffset()));
      text.append(suffix, rangeInSuffix.getStartOffset(), rangeInSuffix.getEndOffset());
      startOffset += rangeInSuffix.getLength();
    }

    return text;
  }

  static final Key<String> UNESCAPED_TEXT = Key.create("INJECTED_UNESCAPED_TEXT");
  private static void storeUnescapedTextFor(final LeafElement leaf, final String leafText) {
    PsiElement psi = leaf.getPsi();
    if (psi != null) {
      psi.putCopyableUserData(UNESCAPED_TEXT, leafText);
    }
  }
}
