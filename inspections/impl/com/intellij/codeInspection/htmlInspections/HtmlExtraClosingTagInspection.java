/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class HtmlExtraClosingTagInspection extends HtmlLocalInspectionTool {

  @Nls
  @NotNull
  public String getDisplayName() {
    return XmlBundle.message("html.inspection.extra.closing.tag");
  }

  @NonNls
  @NotNull
  public String getShortName() {
    return "HtmlExtraClosingTag";
  }

  protected void checkTag(@NotNull final XmlTag tag, @NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    final PsiElement[] children = tag.getChildren();
    String tagName = tag.getName();

    final boolean[] insideEndTag = new boolean[]{false};
    final XmlToken[] startTagNameToken = new XmlToken[]{null};

    final ElementProcessor processor = new ElementProcessor() {
      public void process(@NotNull final XmlToken token, @NotNull final String tagName) {
        String name = tagName;
        if (token.getTokenType() == XmlTokenType.XML_EMPTY_ELEMENT_END) {
          return;
        }

        if (token.getTokenType() == XmlTokenType.XML_END_TAG_START) {
          insideEndTag[0] = true;
        }

        if (token.getTokenType() == XmlTokenType.XML_NAME) {
          if (insideEndTag[0]) {
            String text = token.getText();
            if (tag instanceof HtmlTag) {
              text = text.toLowerCase();
              name = name.toLowerCase();
            }

            boolean isExtraHtmlTagEnd = false;
            if (text.equals(name)) {
              isExtraHtmlTagEnd = tag instanceof HtmlTag && HtmlUtil.isSingleHtmlTag(name);
              if (!isExtraHtmlTagEnd) {
                return;
              }
            }

            assert startTagNameToken != null;

            markClosingTag(token, startTagNameToken[0], tag, isExtraHtmlTagEnd, holder);
          }
          else {
            startTagNameToken[0] = token;
          }
        }
      }
    };

    ProgressManager progressManager = ProgressManager.getInstance();
    for (PsiElement child : children) {
      progressManager.checkCanceled();

      if (child instanceof XmlToken) {
        processor.process((XmlToken) child, tagName);
      } else if (child instanceof PsiErrorElement) {
        final PsiElement[] errorChildren = child.getChildren();
        for (PsiElement errorChild : errorChildren) {
          progressManager.checkCanceled();
          if (errorChild instanceof XmlToken) {
            processor.process((XmlToken) errorChild, tagName);
          }
        }
      }

    }
  }

  interface ElementProcessor {
    void process(@NotNull final XmlToken token, @NotNull final String tagName);
  }

  protected void markClosingTag(@NotNull final XmlToken token,
                                @NotNull final XmlToken startTagNameToken,
                                @NotNull final XmlTag tag,
                                boolean extraClosingTag,
                                @NotNull final ProblemsHolder holder) {
    if (extraClosingTag) {
      holder.registerProblem(token, XmlErrorMessages.message("extra.closing.tag.for.empty.element"),
                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new RemoveExtraClosingTagIntentionAction(token));
    }
  }
}
