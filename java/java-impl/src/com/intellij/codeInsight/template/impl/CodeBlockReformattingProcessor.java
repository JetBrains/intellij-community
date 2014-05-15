/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.template.Template;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.Nls;

/**
 * @author peter
 */
public class CodeBlockReformattingProcessor implements TemplateOptionalProcessor {

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
    return "Please report a bug";
  }

  @Override
  public boolean isEnabled(Template template) {
    return true;
  }

  @Override
  public void setEnabled(Template template, boolean value) {
  }

  @Override
  public boolean isVisible(Template template) {
    return false;
  }
}
