/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon;

import com.intellij.codeHighlighting.RainbowHighlighter;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightVisitor;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.openapi.util.text.StringHash;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public abstract class RainbowVisitor implements HighlightVisitor {
  private HighlightInfoHolder myHolder;
  private RainbowHighlighter myRainbowHighlighter;

  protected static final NotNullLazyKey<HashMap<String, Integer>, PsiElement> USED_COLORS =
    NotNullLazyKey.create("USED_COLORS", psiElement -> new HashMap<String, Integer>());

  @NotNull
  @Override
  public abstract HighlightVisitor clone();

  @NotNull
  protected RainbowHighlighter getHighlighter() {
    return myRainbowHighlighter;
  }

  @Override
  public boolean analyze(@NotNull PsiFile file,
                         boolean updateWholeFile,
                         @NotNull HighlightInfoHolder holder,
                         @NotNull Runnable action) {
    myHolder = holder;
    myRainbowHighlighter = new RainbowHighlighter(myHolder.getColorsScheme());
    try {
      action.run();
    }
    finally {
      myHolder = null;
      myRainbowHighlighter = null;
    }
    return true;
  }

  @Override
  public int order() {
    return 1;
  }

  protected void addInfo(HighlightInfo highlightInfo) {
    myHolder.add(highlightInfo);
  }

  public static boolean existsPassSuitableForFile(@NotNull PsiFile file) {
    for (HighlightVisitor visitor : Extensions.getExtensions(HighlightVisitor.EP_HIGHLIGHT_VISITOR, file.getProject())) {
      if (visitor instanceof RainbowVisitor && visitor.suitableForFile(file)) {
        return true;
      }
    }
    return false;
  }

  protected HighlightInfo getInfo(@NotNull final PsiElement context,
                                  @NotNull final PsiElement rainbowElement,
                                  @NotNull final String id,
                                  @Nullable final TextAttributesKey colorKey) {
    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (context) {
      HashMap<String, Integer> id2index = USED_COLORS.getValue(context);
      Integer colorIndex = id2index.get(id);
      if (colorIndex == null) {
        colorIndex = Math.abs(StringHash.murmur(id, 0x55AA));

        Map<Integer, Integer> index2usage = new HashMap<Integer, Integer>();
        id2index.values().forEach(i -> {
          Integer useCount = index2usage.get(i);
          index2usage.put(i, useCount == null ? 1 : ++useCount);
        });

        int colorsCount = getHighlighter().getColorsCount();
        out:
        for (int cutoff = 0; ; ++cutoff) {
          for (int i = 0; i < colorsCount; ++i) {
            colorIndex %= colorsCount;
            Integer useCount = index2usage.get(colorIndex % colorsCount);
            if (useCount == null) useCount = 0;
            if (useCount == cutoff) break out;
            ++colorIndex;
          }
        }
        id2index.put(id, colorIndex);
      }
      return getHighlighter().getInfo(colorIndex, rainbowElement, colorKey);
    }
  }
}
