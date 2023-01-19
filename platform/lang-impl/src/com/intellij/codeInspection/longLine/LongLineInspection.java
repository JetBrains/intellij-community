/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.longLine;

import com.intellij.application.options.CodeStyle;
import com.intellij.application.options.CodeStyleSchemesConfigurable;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.options.OptPane.pane;
import static com.intellij.codeInspection.options.OptPane.settingLink;

public class LongLineInspection extends LocalInspectionTool {
  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      settingLink(LangBundle.message("link.label.edit.code.style.settings"),
                                       CodeStyleSchemesConfigurable.CONFIGURABLE_ID));
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    PsiFile file = holder.getFile();
    final int codeStyleRightMargin = CodeStyle.getSettings(file).getRightMargin(file.getLanguage());

    final VirtualFile vFile = file.getVirtualFile();
    if (vFile instanceof VirtualFileWindow) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    final Document document = FileDocumentManager.getInstance().getDocument(vFile);
    if (document == null) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    return new PsiElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        int length = element.getTextLength();
        if (element.getTextLength() != 0 && element.getFirstChild() == null && !ignoreFor(element)) {
          int offset = element.getTextOffset();
          int endOffset = offset + length;

          int startLine = document.getLineNumber(offset);
          if (offset > document.getLineStartOffset(startLine) + codeStyleRightMargin) {
            startLine++;
          }

          int endLine = document.getLineNumber(endOffset - 1);

          for (int l = startLine; l <= endLine; l++) {
            int lineEndOffset = document.getLineEndOffset(l);
            int lineMarginOffset = document.getLineStartOffset(l) + codeStyleRightMargin;
            if (lineEndOffset > lineMarginOffset) {
              int highlightingStartOffset = lineMarginOffset - offset;
              int highlightingEndOffset = Math.min(endOffset, lineEndOffset) - offset;
              if (highlightingStartOffset < highlightingEndOffset) {
                TextRange exceedingRange = new TextRange(highlightingStartOffset, highlightingEndOffset);
                holder.registerProblem(element,
                                       exceedingRange,
                                       LangBundle.message("inspection.message.line.longer.than.allowed.by.code.style.columns", codeStyleRightMargin));
              }
            }
          }
        }
      }
    };
  }

  private static boolean ignoreFor(@Nullable PsiElement element) {
    return element != null &&
           LongLineInspectionPolicy.EP_NAME.getExtensionList().stream().anyMatch(policy -> policy.ignoreLongLineFor(element));
  }
}
