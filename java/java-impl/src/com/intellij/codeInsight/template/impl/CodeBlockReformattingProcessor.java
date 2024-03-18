// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.template.Template;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public final class CodeBlockReformattingProcessor implements TemplateOptionalProcessor, DumbAware {

  @Override
  public void processText(Project project,
                          Template template,
                          Document document,
                          RangeMarker templateRange,
                          Editor editor) {
    if (!template.isToReformat()) return;

    PsiDocumentManager.getInstance(project).commitDocument(document);
    PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, project);
    if (!(file instanceof PsiJavaFile)) return;

    CharSequence text = document.getImmutableCharSequence();
    int prevChar = CharArrayUtil.shiftBackward(text, templateRange.getStartOffset() - 1, " \t");
    int nextChar = CharArrayUtil.shiftForward(text, templateRange.getEndOffset(), " \t");
    if (prevChar > 0 && text.charAt(prevChar) == '{' && nextChar < text.length() && text.charAt(nextChar) == '}') {
      PsiCodeBlock codeBlock = PsiTreeUtil.findElementOfClassAtOffset(file, prevChar, PsiCodeBlock.class, false);
      if (codeBlock != null && codeBlock.getTextRange().getStartOffset() == prevChar) {
        PsiJavaToken rBrace = codeBlock.getRBrace();
        if (rBrace != null && rBrace.getTextRange().getStartOffset() == nextChar) {
          CodeEditUtil.markToReformat(rBrace.getNode(), true);
        }
      }
    }
  }

  @Nls
  @Override
  public String getOptionName() {
    return JavaBundle.message("please.report.a.bug");
  }

  @Override
  public boolean isEnabled(Template template) {
    return true;
  }

  @Override
  public boolean isVisible(@NotNull Template template, @NotNull TemplateContext context) {
    return false;
  }
}
