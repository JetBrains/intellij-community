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
package com.intellij.execution.filters;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Map;

/**
 * @author gregsh
 */
public class ExceptionExFilterFactory implements ExceptionFilterFactory {
  @NotNull
  @Override
  public Filter create(@NotNull GlobalSearchScope searchScope) {
    return new MyFilter(searchScope);
  }

  private static class MyFilter implements Filter, FilterMixin {
    private final ExceptionInfoCache myCache;

    public MyFilter(@NotNull final GlobalSearchScope scope) {
      myCache = new ExceptionInfoCache(scope);
    }

    public Result applyFilter(final String line, final int textEndOffset) {
      return null;
    }

    @Override
    public boolean shouldRunHeavy() {
      return true;
    }

    @Override
    public void applyHeavyFilter(@NotNull final Document copiedFragment,
                                 final int startOffset,
                                 int startLineNumber,
                                 @NotNull final Consumer<AdditionalHighlight> consumer) {
      Map<String, Trinity<TextRange, TextRange, TextRange>> visited = new THashMap<>();
      final Trinity<TextRange, TextRange, TextRange> emptyInfo = Trinity.create(null, null, null);

      final ExceptionWorker worker = new ExceptionWorker(myCache);
      for (int i = 0; i < copiedFragment.getLineCount(); i++) {
        final int lineStartOffset = copiedFragment.getLineStartOffset(i);
        final int lineEndOffset = copiedFragment.getLineEndOffset(i);

        String text = copiedFragment.getText(new TextRange(lineStartOffset, lineEndOffset));
        if (!text.contains(".java:")) continue;
        Trinity<TextRange, TextRange, TextRange> info = visited.get(text);
        if (info == emptyInfo) continue;

        if (info == null) {
          info = emptyInfo;
          AccessToken token = ApplicationManager.getApplication().acquireReadActionLock();
          try {
            worker.execute(text, lineEndOffset);
            Result result = worker.getResult();
            if (result == null) continue;
            HyperlinkInfo hyperlinkInfo = result.getHyperlinkInfo();
            if (!(hyperlinkInfo instanceof FileHyperlinkInfo)) continue;

            OpenFileDescriptor descriptor = ((FileHyperlinkInfo)hyperlinkInfo).getDescriptor();
            if (descriptor == null) continue;

            PsiFile psiFile = worker.getFile();
            if (psiFile == null || psiFile instanceof PsiCompiledFile) continue;
            int offset = descriptor.getOffset();
            if (offset <= 0) continue;

            PsiElement element = psiFile.findElementAt(offset);
            PsiTryStatement parent = PsiTreeUtil.getParentOfType(element, PsiTryStatement.class, true, PsiClass.class);
            PsiCodeBlock tryBlock = parent != null? parent.getTryBlock() : null;
            if (tryBlock == null || !tryBlock.getTextRange().contains(offset)) continue;
            info = worker.getInfo();
          }
          finally {
            token.finish();
            visited.put(text, info);
          }
        }
        int off = startOffset + lineStartOffset;
        final Color color = UIUtil.getInactiveTextColor();
        consumer.consume(new AdditionalHighlight(off + info.first.getStartOffset(), off + info.second.getEndOffset()) {
          @NotNull
          @Override
          public TextAttributes getTextAttributes(@Nullable TextAttributes source) {
            return new TextAttributes(null, null, color, EffectType.BOLD_DOTTED_LINE, Font.PLAIN);
          }
        });
      }
    }

    @NotNull
    @Override
    public String getUpdateMessage() {
      return "Highlighting try blocks...";
    }
  }
}
