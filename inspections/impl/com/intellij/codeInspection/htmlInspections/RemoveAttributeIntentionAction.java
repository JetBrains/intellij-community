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
import com.intellij.psi.xml.XmlAttribute;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class RemoveAttributeIntentionAction implements LocalQuickFix {
  private final String myLocalName;
  private final XmlAttribute myAttribute;

  public RemoveAttributeIntentionAction(final String localName, final XmlAttribute attribute) {
    myLocalName = localName;
    myAttribute = attribute;
  }

  @NotNull
  public String getName() {
    return XmlErrorMessages.message("remove.attribute.quickfix.text", myLocalName);
  }

  @NotNull
  public String getFamilyName() {
    return XmlErrorMessages.message("remove.attribute.quickfix.family");
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    if (!CodeInsightUtilBase.prepareFileForWrite(myAttribute.getContainingFile())) {
      return;
    }

    PsiElement next = findNextAttribute(myAttribute);
    new WriteCommandAction(project) {
      protected void run(final Result result) throws Throwable {
        myAttribute.delete();
      }
    }.execute();

    //if (next != null) {
    //  editor.getCaretModel().moveToOffset(next.getTextRange().getStartOffset());
    //}
  }

  private static PsiElement findNextAttribute(final XmlAttribute attribute) {
    PsiElement nextSibling = attribute.getNextSibling();
    while (nextSibling != null) {
      if (nextSibling instanceof XmlAttribute) return nextSibling;
      nextSibling =  nextSibling.getNextSibling();
    }
    return null;
  }
}
