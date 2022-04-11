// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.javaDoc;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.paths.WebReference;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.io.URLUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavadocLinkAsPlainTextInspection extends LocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitDocComment(PsiDocComment comment) {
        if (comment.getOwner() == null) return;
        for (PsiReference reference : ReferenceProvidersRegistry.getReferencesFromProviders(comment)) {
          if (!(reference instanceof WebReference)) continue;
          String url = ((WebReference)reference).getUrl();
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
          holder.registerProblem(comment, range, JavaBundle.message("inspection.javadoc.link.as.plain.text.message"),
                                 new UrlToHtmlFix(comment, start, end));
        }
      }

      private boolean isContentOfATag(String prefix, String suffix) {
        return Pattern.compile("<\\w+\\s+\\w+\\s*=\\s*\"?.*\"?\\s*>.*$", Pattern.DOTALL).matcher(prefix).find() &&
               Pattern.compile("^.*</\\w+>", Pattern.DOTALL).matcher(suffix).find();
      }

      private boolean isHtmlTagAttribute(String prefix, String suffix) {
        return Pattern.compile("<\\w+\\s.*\\w+\\s*=\\s*\"?\\s*$", Pattern.DOTALL).matcher(prefix).find() &&
               Pattern.compile("^\\s*\"?.*>", Pattern.DOTALL).matcher(suffix).find();
      }
    };
  }

  private static class UrlToHtmlFix extends LocalQuickFixAndIntentionActionOnPsiElement {
    private final int myStartOffset;
    private final int myEndOffset;

    protected UrlToHtmlFix(@Nullable PsiElement element, int startOffset, int endOffset) {
      super(element);
      myStartOffset = startOffset;
      myEndOffset = endOffset;
    }

    @Override
    public void invoke(@NotNull Project project,
                       @NotNull PsiFile file,
                       @Nullable Editor editor,
                       @NotNull PsiElement startElement,
                       @NotNull PsiElement endElement) {
      String commentText = startElement.getText();
      String prefix = commentText.substring(0, myStartOffset);
      String url = commentText.substring(myStartOffset, myEndOffset);
      String suffix = commentText.substring(myEndOffset);
      String dummyText = "...";
      String wrappedLink = "<a href=\"" + url + "\">" + dummyText + "</a>";
      CommentTracker ct = new CommentTracker();
      PsiElement replacement = ct.replace(startElement, prefix + wrappedLink + suffix);
      if (editor != null) {
        int start = replacement.getTextRange().getStartOffset() + prefix.length() + url.length() + 11;
        int end = start + dummyText.length();
        editor.getCaretModel().moveToOffset(start);
        editor.getSelectionModel().setSelection(start, end);
      }
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
