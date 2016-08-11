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

  protected HighlightInfo getInfo(@NotNull PsiElement context,
                                  @NotNull PsiElement rainbowElement,
                                  @NotNull String id,
                                  @Nullable TextAttributesKey colorKey) {
    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (context) {
      return getHighlighter()
        .getInfo(getHighlighter().getColorIndex(
          USED_COLORS.getValue(context),
          id,
          RainbowHighlighter.getRainbowHash(id)),
                 rainbowElement,
                 colorKey);
    }
  }
}
