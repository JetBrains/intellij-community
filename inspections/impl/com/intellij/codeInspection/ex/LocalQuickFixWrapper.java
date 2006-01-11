package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.QuickFix;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.openapi.actionSystem.AnActionEvent;

/**
 * @author max
 */
public class LocalQuickFixWrapper extends QuickFixAction {
  private QuickFix myFix;
  private String myText;
  public LocalQuickFixWrapper(QuickFix fix, DescriptorProviderInspection tool) {
    super(fix.getName(), tool);
    myTool = tool;
    myFix = fix;
    myText = myFix.getName();
  }

  public void update(AnActionEvent e) {
    super.update(e);
    getTemplatePresentation().setText(myText);
    e.getPresentation().setText(myText);
  }

  public String getText(RefEntity where) {
    return myText;
  }

  public void setText(final String text) {
    myText = text;
  }

  protected boolean applyFix(RefElement[] refElements) {
   /* dead code ?!
     for (RefElement refElement : refElements) {
      ProblemDescriptor[] problems = myTool.getDescriptions(refElement);
      if (problems != null) {
        PsiElement psiElement = refElement.getElement();
        if (psiElement != null) {
          for (ProblemDescriptor problem : problems) {
            LocalQuickFix fix = problem.getFix();
            if (fix != null) {
              fix.applyFix(psiElement.getProject(), problem);
              myTool.ignoreProblem(refElement, problem);
            }
          }
        }
      }
    }*/

    return true;
  }

  protected boolean isProblemDescriptorsAcceptable() {
    return true;
  }

  public QuickFix getFix() {
    return myFix;
  }
}
