/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.tree.IElementType;
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

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  protected void checkTag(@NotNull final XmlTag tag, @NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    ProgressManager progressManager = ProgressManager.getInstance();

    final String name = tag.getName();
    final boolean htmlTag = tag instanceof HtmlTag;
    final String tagName = htmlTag ? name.toLowerCase() : name;
    boolean matchesNothing = false;

    final PsiElement[] children = tag.getChildren();
    for (int i = children.length - 1; i >= 0; i--) {
      progressManager.checkCanceled();

      final PsiElement child = children[i];
      if (child instanceof XmlToken) {
        final XmlToken token = (XmlToken)child;

        final IElementType type = token.getTokenType();
        if (XmlTokenType.XML_NAME == type) {
          if (isEndTagName(token)) {
            final String tokenName = htmlTag ? token.getText().toLowerCase() : token.getText();

            if (tagName.equals(tokenName)) {
              matchesNothing = true;

              if (htmlTag && HtmlUtil.isSingleHtmlTag(name)) {
                markClosingTag(token, tag, matchesNothing, holder, tag);
              }
            } else {
              markClosingTag(token, tag, matchesNothing, holder, tag);
            }
          }
        }
      }
      else if (child instanceof PsiErrorElement) {
        final PsiElement[] errorChildren = child.getChildren();
        boolean insideEndTag = false;
        for (final PsiElement each : errorChildren) {
          if (each instanceof XmlToken) {
            final XmlToken token = (XmlToken)each;
            if (token.getTokenType() == XmlTokenType.XML_END_TAG_START) {
              insideEndTag = true;
              continue;
            }

            if (insideEndTag && token.getTokenType() == XmlTokenType.XML_NAME) {
              final String tokenName = htmlTag ? token.getText().toLowerCase() : token.getText();
              if (!tagName.equals(tokenName)) {
                markClosingTag((XmlToken)each, tag, matchesNothing, holder, child);
              }
              break;
            }
          }
        }
      }
    }
  }

  private static boolean isEndTagName(@NotNull final XmlToken token) {
    final PsiElement prevSibling = token.getPrevSibling();
    return prevSibling instanceof XmlToken && ((XmlToken)prevSibling).getTokenType() == XmlTokenType.XML_END_TAG_START;

  }

  protected void markClosingTag(@NotNull final XmlToken token,
                                @NotNull final XmlTag tag,
                                boolean extraClosingTag,
                                @NotNull final ProblemsHolder holder,
                                @NotNull final PsiElement parent) {
    if (extraClosingTag) {
      final boolean errorElement = parent instanceof PsiErrorElement &&  ((PsiErrorElement)parent).getErrorDescription().equals(XmlErrorMessages.message("xml.parsing.closing.tag.mathes.nothing"));
      final PsiElement target = errorElement ? parent : token;

      final RemoveExtraClosingTagIntentionAction action = new RemoveExtraClosingTagIntentionAction(token);
      holder.registerProblem(target, XmlErrorMessages.message("extra.closing.tag.for.empty.element"),
                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING, action);
    }
  }
}
