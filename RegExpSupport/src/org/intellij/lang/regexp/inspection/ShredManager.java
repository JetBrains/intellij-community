// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp.inspection;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiLiteralValue;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;

/**
 * Encapsulates a work with {@link PsiLanguageInjectionHost.Shred}.
 * An injection process overprints on the injected text which is considered as RegExp file.
 * This text may contain some bogus symbols which are needed to be deleted
 * to find a corresponding host element in the PSI Java file.
 */
class ShredManager {
  // reserved keyword (org.intellij.plugins.intelliLang.util.ContextComputationProcessor.collectOperands)
  private static final String PSI_EXPR_MASK = "missingValue";
  private static final String PSI_CONDITIONAL_EXPR_MASK = " ";

  private final PsiElement myPsiElement;
  private final InjectedLanguageManager myInstance;
  private final PsiLanguageInjectionHost myHost;

  ShredManager(@NotNull PsiElement psiElement) {
    myPsiElement = psiElement;
    myInstance = InjectedLanguageManager.getInstance(psiElement.getProject());
    myHost = myInstance.getInjectionHost(psiElement);
  }

  @Nullable
  ShredInfo getShredInfo(@NotNull String text) {
    if (myHost == null) return null;

    Ref<ShredInfo> result = Ref.create();
    myInstance.enumerate(myHost, (file, shreds) -> {
      Iterator<ShredInfo> shredsIterator = new ShredsIterator(shreds);
      int shredSymbolCounter = 0;
      ShredInfo nextShred = null;
      int elementOffset = calculateElementOffset(text);
      while (shredsIterator.hasNext() && shredSymbolCounter <= elementOffset) {
        nextShred = shredsIterator.next();
        if (nextShred.symbol == ' ') continue;
        shredSymbolCounter++;
      }
      result.set(nextShred);
    });
    return result.get();
  }

  boolean containsCloseRealWhiteSpace(@NotNull ShredInfo shredInfo, final boolean after) {
    Ref<Boolean> result = Ref.create(false);
    myInstance.enumerate(myHost, (file, shreds) -> {
      if (shredInfo.shredIndex >= shreds.size()) return;
      PsiLanguageInjectionHost.Shred shred = shreds.get(shredInfo.shredIndex);
      String shredText = extractShredText(shred);
      if (shredText == null) return;
      int whiteSpaceIndex = after ? shredInfo.symbolIndex + 1 : shredInfo.symbolIndex - 1;
      if (whiteSpaceIndex < 0 || whiteSpaceIndex >= shredText.length()) return;
      result.set(shredText.charAt(whiteSpaceIndex) == ' ');
    });
    return result.get();
  }

  private int calculateElementOffset(@NotNull String text) {
    int offset = myPsiElement.getTextOffset();
    if (offset > text.length()) return -1;
    String cutText = text.substring(0, offset);
    String cutTextWithoutBogusWords = cutText.replaceAll(PSI_EXPR_MASK, "").replaceAll(PSI_CONDITIONAL_EXPR_MASK, "");
    return offset - (cutText.length() - cutTextWithoutBogusWords.length());
  }

  @Nullable
  private static String extractShredText(@NotNull PsiLanguageInjectionHost.Shred shred) {
    PsiLiteralValue shredLiteralVal = ObjectUtils.tryCast(shred.getHost(), PsiLiteralValue.class);
    if (shredLiteralVal == null || shredLiteralVal.getValue() == null) return null;
    return String.valueOf(shredLiteralVal.getValue());
  }

  private static class ShredsIterator implements Iterator<ShredInfo> {
    private final List<PsiLanguageInjectionHost.Shred> myShreds;

    private int myShredIndex = -1;
    private String myShredText;
    private int mySymbolIndex = -1;

    private ShredsIterator(@NotNull List<PsiLanguageInjectionHost.Shred> shreds) {
      myShreds = shreds;
      if (!shreds.isEmpty()) {
        myShredIndex = 0;
      }
    }

    @Override
    public boolean hasNext() {
      return myShredIndex != -1 && findFirstNonEmptyShredIndex() != -1;
    }

    @Override
    @NotNull
    public ShredInfo next() {
      if (myShredIndex == -1) throw new IllegalStateException("Iterator doesn't contain any shreds");
      myShredIndex = findFirstNonEmptyShredIndex();
      if (myShredIndex == -1) throw new IllegalStateException("Iterator doesn't contain non-empty shreds");

      if (myShredText == null) {
        myShredText = extractShredText(myShreds.get(myShredIndex));
        if (StringUtil.isEmpty(myShredText)) throw new IllegalStateException("Current shred text is empty");
        mySymbolIndex = 0;
      }
      int shredIndex = myShredIndex;
      char shredSymbol = myShredText.charAt(mySymbolIndex);
      int symbolIndex = mySymbolIndex;
      PsiLanguageInjectionHost.Shred shred = myShreds.get(this.myShredIndex);
      updateIndexes();
      return new ShredInfo(shredIndex, shredSymbol, symbolIndex, shred.getHost());
    }

    private int findFirstNonEmptyShredIndex() {
      for (int shredIndex = myShredIndex; shredIndex < myShreds.size(); shredIndex++) {
        String shredText = extractShredText(myShreds.get(shredIndex));
        if (!StringUtil.isEmpty(shredText)) return shredIndex;
      }
      return -1;
    }

    private void updateIndexes() {
      if (mySymbolIndex < myShredText.length() - 1) {
        mySymbolIndex++;
      }
      else if (myShredIndex < myShreds.size() - 1) {
        myShredIndex++;
        myShredText = null;
      }
      else {
        myShredIndex = -1;
        myShredText = null;
        mySymbolIndex = -1;
      }
    }
  }

  static class ShredInfo {
    private final int symbolIndex;
    private final char symbol;
    private final int shredIndex;
    private final PsiElement myHost;

    private ShredInfo(int shredIndex, char symbol, int symbolIndex, @Nullable PsiElement host) {
      this.shredIndex = shredIndex;
      this.symbol = symbol;
      this.symbolIndex = symbolIndex;
      this.myHost = host;
    }

    @Nullable
    PsiElement getHost() {
      return myHost;
    }
  }
}
