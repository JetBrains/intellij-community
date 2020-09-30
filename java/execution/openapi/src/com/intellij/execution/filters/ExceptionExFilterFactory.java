// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * @author gregsh
 */
public final class ExceptionExFilterFactory implements ExceptionFilterFactory {
  @NotNull
  @Override
  public Filter create(@NotNull GlobalSearchScope searchScope) {
    return new MyFilter(searchScope);
  }

  private static class MyFilter implements Filter, FilterMixin {
    private final ExceptionInfoCache myCache;

    MyFilter(@NotNull final GlobalSearchScope scope) {
      myCache = new ExceptionInfoCache(scope);
    }

    @Override
    public Result applyFilter(@NotNull final String line, final int textEndOffset) {
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
                                 @NotNull final Consumer<? super AdditionalHighlight> consumer) {
      Map<String, ExceptionWorker.ParsedLine> visited = new HashMap<>();
      ExceptionWorker.ParsedLine emptyInfo = new ExceptionWorker.ParsedLine(TextRange.EMPTY_RANGE, TextRange.EMPTY_RANGE, TextRange.EMPTY_RANGE, null, -1);

      final ExceptionWorker worker = new ExceptionWorker(myCache);
      for (int i = 0; i < copiedFragment.getLineCount(); i++) {
        final int lineStartOffset = copiedFragment.getLineStartOffset(i);
        final int lineEndOffset = copiedFragment.getLineEndOffset(i);

        String lineText = copiedFragment.getText(new TextRange(lineStartOffset, lineEndOffset));
        if (!lineText.contains(".java:")) continue;
        ExceptionWorker.ParsedLine info = visited.get(lineText);
        if (info == emptyInfo) continue;

        if (info == null) {
          info = ReadAction.compute(() -> DumbService.isDumb(worker.getProject()) ? null : doParse(worker, lineEndOffset, lineText));
          visited.put(lineText, info == null ? emptyInfo : info);
          if (info == null) {
            continue;
          }
        }
        int off = startOffset + lineStartOffset;
        final Color color = UIUtil.getInactiveTextColor();
        consumer.consume(new AdditionalHighlight(off + info.classFqnRange.getStartOffset(), off + info.methodNameRange.getEndOffset()) {
          @NotNull
          @Override
          public TextAttributes getTextAttributes(@Nullable TextAttributes source) {
            return new TextAttributes(null, null, color, EffectType.BOLD_DOTTED_LINE, Font.PLAIN);
          }
        });
      }
    }

    private static ExceptionWorker.ParsedLine doParse(@NotNull ExceptionWorker worker, int lineEndOffset, @NotNull String lineText) {
      Result result = worker.execute(lineText, lineEndOffset);
      if (result == null) return null;
      HyperlinkInfo hyperlinkInfo = result.getHyperlinkInfo();
      if (!(hyperlinkInfo instanceof FileHyperlinkInfo)) return null;

      OpenFileDescriptor descriptor = ((FileHyperlinkInfo)hyperlinkInfo).getDescriptor();
      if (descriptor == null) return null;

      PsiFile psiFile = worker.getFile();
      if (psiFile == null || psiFile instanceof PsiCompiledFile) return null;
      int offset = descriptor.getOffset();
      if (offset <= 0) return null;

      PsiElement element = psiFile.findElementAt(offset);
      PsiTryStatement parent = PsiTreeUtil.getParentOfType(element, PsiTryStatement.class, true, PsiClass.class);
      PsiCodeBlock tryBlock = parent != null? parent.getTryBlock() : null;
      if (tryBlock == null || !tryBlock.getTextRange().contains(offset)) return null;
      return worker.getInfo();
    }

    @NotNull
    @Override
    public String getUpdateMessage() {
      return "Highlighting try blocks...";
    }
  }
}
