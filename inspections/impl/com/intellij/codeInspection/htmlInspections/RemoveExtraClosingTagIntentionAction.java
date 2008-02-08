/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class RemoveExtraClosingTagIntentionAction implements LocalQuickFix {

  private final XmlToken myXmlToken;

  public RemoveExtraClosingTagIntentionAction(@NotNull final XmlToken xmlToken) {
    myXmlToken = xmlToken;
  }


  @NotNull
  public String getFamilyName() {
    return XmlErrorMessages.message("remove.extra.closing.tag.quickfix");
  }

  @NotNull
  public String getName() {
    return XmlErrorMessages.message("remove.extra.closing.tag.quickfix");
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    if (!CodeInsightUtilBase.prepareFileForWrite(myXmlToken.getContainingFile())) {
      return;
    }

    new WriteCommandAction(project) {
      protected void run(final Result result) throws Throwable {
        XmlToken tagEndStart = myXmlToken;
        while(tagEndStart.getTokenType() != XmlTokenType.XML_END_TAG_START) {
          final PsiElement prevSibling = tagEndStart.getPrevSibling();
          if (!(prevSibling instanceof XmlToken)) break;
          tagEndStart = (XmlToken)prevSibling;
        }

        final PsiElement parent = tagEndStart.getParent();
        parent.deleteChildRange(tagEndStart, parent.getLastChild());
      }
    }.execute();
  }
}
