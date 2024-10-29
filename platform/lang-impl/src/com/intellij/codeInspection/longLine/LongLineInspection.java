// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.longLine;

import com.intellij.application.options.CodeStyle;
import com.intellij.application.options.CodeStyleSchemesConfigurable;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.lang.LangBundle;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.options.OptPane.pane;
import static com.intellij.codeInspection.options.OptPane.settingLink;

public final class LongLineInspection extends LocalInspectionTool {
  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(settingLink(LangBundle.message("link.label.edit.code.style.settings"), CodeStyleSchemesConfigurable.CONFIGURABLE_ID));
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    final PsiFile file = holder.getFile();
    if (InjectedLanguageManager.getInstance(file.getProject()).getInjectionHost(file) != null) return PsiElementVisitor.EMPTY_VISITOR;

    final Document document = file.getViewProvider().getDocument();
    if (document == null) return PsiElementVisitor.EMPTY_VISITOR;

    final CodeStyleSettings codeStyleSettings = CodeStyle.getSettings(file);
    final int codeStyleRightMargin = codeStyleSettings.getRightMargin(file.getLanguage());
    final int tabSize = codeStyleSettings.getTabSize(file.getFileType());
    final TextRange restrictRange = session.getRestrictRange();
    final CharSequence text = document.getImmutableCharSequence();
    final int lineCount = document.getLineCount();
    return new PsiElementVisitor() {

      @Override
      public void visitFile(@NotNull PsiFile file) {
        final TextRange range = restrictRange.intersection(file.getTextRange());
        if (range == null || range.isEmpty()) return;

        int line = document.getLineNumber(range.getStartOffset());
        while (true) {
          final int lineStart = document.getLineStartOffset(line);
          if (lineStart > range.getEndOffset()) {
            break;
          }
          final int lineEnd = document.getLineEndOffset(line);
          int count = 0;
          for (int i = lineStart; i < lineEnd; i++) {
            count += (text.charAt(i) == '\t') ? tabSize : 1;
            if (count > codeStyleRightMargin) {
              String message =
                LangBundle.message("inspection.message.line.longer.than.allowed.by.code.style.columns", codeStyleRightMargin);
              final TextRange problemRange = new TextRange(i, lineEnd);
              final PsiElement element = findElementInRange(file, problemRange);
              if (!ignoreFor(element)) {
                holder.registerProblem(element, problemRange.shiftLeft(element.getTextRange().getStartOffset()), message);
              }
              break;
            }
          }
          line++;
          if (line >= lineCount) break;
        }
      }
    };
  }

  private static @Nullable PsiElement findElementInRange(@NotNull PsiFile file, @NotNull TextRange range) {
    PsiElement left = file.findElementAt(range.getStartOffset());
    if (left == null) return null;
    PsiElement right = file.findElementAt(range.getEndOffset() - 1);
    if (right == null) return null;
    return PsiTreeUtil.findCommonParent(left, right);
  }

  private static boolean ignoreFor(@Nullable PsiElement element) {
    if (element == null) return true;
    return ContainerUtil.exists(LongLineInspectionPolicy.EP_NAME.getExtensionList(), policy -> policy.ignoreLongLineFor(element));
  }
}
