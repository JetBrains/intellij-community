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
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class RenameTagBeginOrEndIntentionAction implements LocalQuickFix {

  private boolean myStart;
  private CompositeElement myParent;
  private XmlToken myTarget;
  private String myTargetName;
  private String mySourceName;

  RenameTagBeginOrEndIntentionAction(@NotNull final CompositeElement parent,
                                     @NotNull final XmlToken target,
                                     @NotNull final String targetName,
                                     @NotNull final String sourceName,
                                     final boolean start) {
    myParent = parent;
    myTarget = target;
    myTargetName = targetName;
    mySourceName = sourceName;
    myStart = start;
  }

  @NotNull
  public String getFamilyName() {
    return getName();
  }

  @NotNull
  public String getName() {
    return myStart
           ? XmlErrorMessages.message("rename.start.tag.name.intention", mySourceName, myTargetName)
           : XmlErrorMessages.message("rename.end.tag.name.intention", mySourceName, myTargetName);
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    if (myTarget.isValid()) {
      if (!CodeInsightUtilBase.prepareFileForWrite(myTarget.getContainingFile())) {
        return;
      }

      new WriteCommandAction(project) {
        protected void run(final Result result) throws Throwable {
          final XmlTag newTag = JavaPsiFacade.getInstance(project).getElementFactory().createTagFromText("<" + myTargetName + "/>");
          CodeEditUtil.replaceChild(myParent, myTarget.getNode(), newTag.getChildren()[1].getNode());
        }
      }.execute();
    }
  }
}
