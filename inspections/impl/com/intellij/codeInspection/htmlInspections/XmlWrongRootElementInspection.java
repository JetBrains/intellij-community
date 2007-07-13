/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
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
public class XmlWrongRootElementInspection extends HtmlLocalInspectionTool {

  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.XML_INSPECTIONS;
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return XmlBundle.message("xml.inspection.wrong.root.element");
  }

  @NonNls
  @NotNull
  public String getShortName() {
    return "XmlWrongRootElement";
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

      final XmlDocument document = xmlFile.getDocument();
      if (document == null) {
        return;
      }

      XmlProlog prolog = document.getProlog();
      if (prolog == null || prolog.getUserData(DO_NOT_VALIDATE_KEY) != null) {
        return;
      }

      final XmlDoctype doctype = prolog.getDoctype();

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
          holder.registerProblem(
            tag,
            XmlErrorMessages.message("wrong.root.element"),
            ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
            new LocalQuickFix() {
              public String getName() {
                return XmlBundle.message("change.root.element.to",doctype.getNameElement().getText());
              }

              public String getFamilyName() {
                return getName();
              }

              public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
                if (!CodeInsightUtil.prepareFileForWrite(tag.getContainingFile())) {
                  return;
                }

                new WriteCommandAction(project) {
                  protected void run(final Result result) throws Throwable {
                    tag.setName(doctype.getNameElement().getText());
                  }
                }.execute();
              }
            }
          );
        }
      }
    }
  }
}
