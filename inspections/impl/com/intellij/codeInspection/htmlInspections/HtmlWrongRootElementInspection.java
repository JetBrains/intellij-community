/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiFile;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.xml.*;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class HtmlWrongRootElementInspection extends HtmlLocalInspectionTool {

  @Nls
  @NotNull
  public String getDisplayName() {
    return XmlBundle.message("html.inspection.wrong.root.element");
  }

  @NonNls
  @NotNull
  public String getShortName() {
    return "HtmlWrongRootElement";
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  protected void checkTag(@NotNull final XmlTag tag, @NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    if (!(tag.getParent() instanceof XmlTag)) {
      final PsiFile psiFile = tag.getContainingFile();
      if (!(psiFile instanceof XmlFile)) {
        return;
      }

      XmlFile xmlFile = (XmlFile) psiFile;

      /*
      if (psiFile instanceof XmlFile) {
        xmlFile = (XmlFile)psiFile;
      }
      else {
        // jsp?
        final JspFile jspFile = PsiUtil.getJspFile(tag);
        if (jspFile != null) {
          xmlFile = jspFile;
        }
      }
      */

      final XmlDocument document = xmlFile.getDocument();
      if (document == null) {
        return;
      }

      XmlProlog prolog = document.getProlog();
      if (prolog == null || prolog.getUserData(DO_NOT_VALIDATE_KEY) != null) {
        return;
      }

      XmlDoctype doctype = prolog.getDoctype();

      if (doctype == null) {
        return;
      }

      XmlElement nameElement = doctype.getNameElement();

      if (nameElement == null) {
        return;
      }

      String name = tag.getName();
      String text = nameElement.getText();
      if (tag instanceof HtmlTag) {
        name = name.toLowerCase();
        text = text.toLowerCase();
      }

      if (!name.equals(text)) {
        name = XmlUtil.findLocalNameByQualifiedName(name);

        if (!name.equals(text)) {
          holder.registerProblem(tag, XmlErrorMessages.message("wrong.root.element"), ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
        }
      }
    }
  }
}
