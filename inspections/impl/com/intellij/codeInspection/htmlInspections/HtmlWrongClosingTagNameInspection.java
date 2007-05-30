/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.PsiElement;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class HtmlWrongClosingTagNameInspection extends HtmlExtraClosingTagInspection {

  @Nls
  @NotNull
  public String getDisplayName() {
    return XmlBundle.message("html.inspection.wrong.closing.tag");
  }

  @NonNls
  @NotNull
  public String getShortName() {
    return "HtmlWrongClosingTagName";
  }


  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  protected void markClosingTag(@NotNull final XmlToken token,
                                @NotNull final XmlTag tag,
                                final boolean extraClosingTag,
                                @NotNull final ProblemsHolder holder, @NotNull final PsiElement tokenParent) {

    if (!extraClosingTag) {
      final String tokenText = (tag instanceof HtmlTag) ? token.getText().toLowerCase() : token.getText();
      final String tagName = (tag instanceof HtmlTag) ? tag.getName().toLowerCase() : tag.getName();

      final RenameTagBeginOrEndIntentionAction action1 = new RenameTagBeginOrEndIntentionAction((CompositeElement) tokenParent, token, tagName, tokenText, false);
      final RenameTagBeginOrEndIntentionAction action2 = new RenameTagBeginOrEndIntentionAction((CompositeElement) tag, (XmlToken)XmlChildRole.START_TAG_NAME_FINDER.findChild(tag.getNode()), tokenText, tagName, true);

      holder.registerProblem(token, XmlErrorMessages.message("wrong.closing.tag.name"), ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                             new RemoveExtraClosingTagIntentionAction(token), action1, action2);

      final ASTNode astNode = tag.getNode();
      assert astNode != null;

      final ASTNode endOfTagStart = XmlChildRole.START_TAG_END_FINDER.findChild(astNode);
      final ASTNode startOfTagStart = XmlChildRole.START_TAG_START_FINDER.findChild(astNode);
      if (endOfTagStart != null && startOfTagStart != null) {
        final TextRange textRange = new TextRange(startOfTagStart.getPsi().getStartOffsetInParent() + startOfTagStart.getTextLength(),
                                                  endOfTagStart.getPsi().getStartOffsetInParent());
        holder.registerProblem(holder.getManager().createProblemDescriptor(tag, textRange, XmlErrorMessages.message("tag.has.wrong.closing.tag.name"),
                                                                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING, action1, action2));
      }
      else {
        final ASTNode startTagNameNode = XmlChildRole.START_TAG_NAME_FINDER.findChild(astNode);
        holder.registerProblem(startTagNameNode.getPsi(), XmlErrorMessages.message("tag.has.wrong.closing.tag.name"),
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING, action1, action2);
      }
    }
  }
}
