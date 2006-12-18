/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.application.Result;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.PsiFile;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class RenameTagBeginOrEndIntentionAction implements LocalQuickFix {

  private boolean myStart;
  private XmlTag myTagToChange;
  private String myName;

  RenameTagBeginOrEndIntentionAction(XmlTag tagToChange, String name, boolean start) {
    myStart = start;
    myTagToChange = tagToChange;
    myName = name;
  }

  @NotNull
  public String getFamilyName() {
    return getName();
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
  }

  @NotNull
  public String getName() {
    return myStart ? XmlErrorMessages.message("rename.start.tag.name.intention") : XmlErrorMessages.message("rename.end.tag.name.intention");
  }

  public void applyFix(@NotNull final Project project, final ProblemDescriptor descriptor) {
    if (!CodeInsightUtil.prepareFileForWrite(myTagToChange.getContainingFile())) {
      return;
    }

    new WriteCommandAction(project) {
      protected void run(final Result result) throws Throwable {
        myTagToChange.setName(myName);
      }
    }.execute();
  }
}
