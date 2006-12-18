/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
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
    String name = tag.getName();

    boolean insideEndTag = false;
    XmlToken startTagNameToken = null;

    ProgressManager progressManager = ProgressManager.getInstance();
    for (PsiElement child : children) {
      progressManager.checkCanceled();

      if (child instanceof XmlToken) {
        final XmlToken xmlToken = (XmlToken)child;
        if (xmlToken.getTokenType() == XmlTokenType.XML_EMPTY_ELEMENT_END) {
          return;
        }

        if (xmlToken.getTokenType() == XmlTokenType.XML_END_TAG_START) {
          insideEndTag = true;
        }

        if (xmlToken.getTokenType() == XmlTokenType.XML_NAME) {
          if (insideEndTag) {
            String text = xmlToken.getText();
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

            markClosingTag(xmlToken, startTagNameToken, tag, isExtraHtmlTagEnd, holder);
            return;
          }
          else {
            startTagNameToken = xmlToken;
          }
        }
      }
    }

    //return tag instanceof HtmlTag &&
    //       (HtmlUtil.isOptionalEndForHtmlTag(name) ||
    //        HtmlUtil.isSingleHtmlTag(name)
    //       );
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
