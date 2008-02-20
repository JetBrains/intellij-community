/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.util.XmlTagUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class RenameTagBeginOrEndIntentionAction implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.htmlInspections.RenameTagBeginOrEndIntentionAction");

  private boolean myStart;
  private String myTargetName;
  private String mySourceName;

  RenameTagBeginOrEndIntentionAction(@NotNull final String targetName, @NotNull final String sourceName, final boolean start) {
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
    final PsiElement psiElement = descriptor.getPsiElement();
    if (!psiElement.isValid()) return;
    if (!CodeInsightUtilBase.prepareFileForWrite(psiElement.getContainingFile())) return;

    if (psiElement instanceof XmlToken) {
      PsiElement target = null;
      final String text = psiElement.getText();
      if (!myTargetName.equals(text)) {
        target = psiElement;
      }
      else {
        // we're in the other
        PsiElement parent = psiElement.getParent();
        if (parent instanceof PsiErrorElement) {
          parent = parent.getParent();
        }

        if (parent instanceof XmlTag) {
          if (myStart) {
            target = XmlTagUtil.getStartTagNameElement((XmlTag)parent);
          }
          else {
            target = XmlTagUtil.getEndTagNameElement((XmlTag) parent);
            if (target == null) {
              final PsiErrorElement errorElement = PsiTreeUtil.getChildOfType(parent, PsiErrorElement.class);
              target = XmlWrongClosingTagNameInspection.findEndTagName(errorElement);
            }
          }
        }
      }

      if (target != null) {
        try {
          final XmlTag newTag = JavaPsiFacade.getInstance(project).getElementFactory().createTagFromText("<" + myTargetName + "/>");
          target.replace(newTag.getChildren()[1]);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }

    }
  }
}
