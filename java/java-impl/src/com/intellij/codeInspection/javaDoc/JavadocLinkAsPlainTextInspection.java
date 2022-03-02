// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.javaDoc;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;

public class JavadocLinkAsPlainTextInspection extends LocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitDocToken(PsiDocToken token) {
        super.visitDocToken(token);
        String text = token.getText();
        Matcher matcher = URLUtil.URL_PATTERN.matcher(text);
        while (matcher.find()) {
          int start = matcher.start();
          int end = matcher.end();
          String prefix = text.substring(0, start);
          String suffix = text.substring(end);
          if ((prefix.matches(".*<a href=\".*\">$") && suffix.matches("^</a>.*")) ||
              (prefix.endsWith("<a href=\"") && suffix.matches("\">.*</a>.*"))) {
            continue;
          }
          TextRange range = TextRange.create(start, end);
          holder.registerProblem(token, range, JavaBundle.message("inspection.javadoc.link.as.plain.text.message"),
                                 new UrlToHtmlFix(token, range));
        }
      }
    };
  }

  private static class UrlToHtmlFix extends LocalQuickFixAndIntentionActionOnPsiElement {
    private final TextRange myRange;

    protected UrlToHtmlFix(@Nullable PsiElement element, TextRange range) {
      super(element);
      myRange = range;
    }

    @Override
    public void invoke(@NotNull Project project,
                       @NotNull PsiFile file,
                       @Nullable Editor editor,
                       @NotNull PsiElement startElement,
                       @NotNull PsiElement endElement) {
      Document document = PsiDocumentManager.getInstance(project).getDocument(file);
      if (document == null) return;
      String text = myRange.substring(startElement.getText());
      int offset = startElement.getTextOffset();
      String wrappedLink = "<a href=\"" + text + "\">" + text + "</a>";
      document.replaceString(offset + myRange.getStartOffset(), offset + myRange.getEndOffset(),
                             wrappedLink);
    }

    @Override
    public @NotNull String getText() {
      return JavaBundle.message("inspection.javadoc.link.as.plain.text.fix.name");
    }

    @Override
    public @NotNull String getFamilyName() {
      return JavaBundle.message("inspection.javadoc.link.as.plain.text.family.name");
    }
  }
}
