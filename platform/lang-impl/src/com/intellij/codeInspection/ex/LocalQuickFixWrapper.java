/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.QuickFix;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.openapi.actionSystem.AnActionEvent;

/**
 * @author max
 */
public class LocalQuickFixWrapper extends QuickFixAction {
  private final QuickFix myFix;
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
