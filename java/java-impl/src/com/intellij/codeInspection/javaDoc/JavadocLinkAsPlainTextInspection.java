// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.javaDoc;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.paths.WebReference;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavadocLinkAsPlainTextInspection extends LocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitDocComment(@NotNull PsiDocComment comment) {
        if (comment.getOwner() == null) return;
        for (PsiReference reference : ReferenceProvidersRegistry.getReferencesFromProviders(comment)) {
          if (!(reference instanceof WebReference)) continue;
          String url = ((WebReference)reference).getValue();
          if (!url.startsWith(URLUtil.HTTP_PROTOCOL) && !url.startsWith(URLUtil.HTTPS_PROTOCOL)) return;
          String commentText = comment.getText();
          Pattern pattern = Pattern.compile("\n\\s*\\*");
          Matcher matcher = pattern.matcher(commentText);
          commentText = matcher.replaceAll(result -> " ".repeat(result.group().length()));
          TextRange range = reference.getRangeInElement();
          int start = range.getStartOffset();
          int end = range.getEndOffset();
          PsiElement element = comment.findElementAt(start);
          if (element == null) return;
          PsiDocTag tag = PsiTreeUtil.getParentOfType(element, PsiDocTag.class);
          if (tag != null) {
            String tagName = tag.getName();
            if (tagName.equals("see")) continue; // handled by JavaDocReferenceInspection
            if (tagName.equals("code")) continue;
          }
          String prefix = commentText.substring(0, start);
          String suffix = commentText.substring(end);
          if (isContentOfATag(prefix, suffix) || isHtmlTagAttribute(prefix, suffix)) continue;
          holder.problem(comment, JavaBundle.message("inspection.javadoc.link.as.plain.text.message"))
            .range(range).fix(new UrlToHtmlFix(comment, start, end)).register();
        }
      }

      private static boolean isContentOfATag(String prefix, String suffix) {
        return Pattern.compile("<\\w+\\s+\\w+\\s*=\\s*\"?.*\"?\\s*>.*$", Pattern.DOTALL).matcher(prefix).find() &&
               Pattern.compile("^.*</\\w+>", Pattern.DOTALL).matcher(suffix).find();
      }

      private static boolean isHtmlTagAttribute(String prefix, String suffix) {
        return Pattern.compile("<\\w+\\s.*\\w+\\s*=\\s*\"?\\s*$", Pattern.DOTALL).matcher(prefix).find() &&
               Pattern.compile("^\\s*\"?.*>", Pattern.DOTALL).matcher(suffix).find();
      }
    };
  }
}
