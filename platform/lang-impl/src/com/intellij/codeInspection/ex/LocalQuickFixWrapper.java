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

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Set;

/**
 * @author max
 */
public class LocalQuickFixWrapper extends QuickFixAction {
  private final QuickFix myFix;
  private String myText;

  public LocalQuickFixWrapper(@NotNull QuickFix fix, @NotNull DescriptorProviderInspection tool) {
    super(fix.getName(), tool);
    myTool = tool;
    myFix = fix;
    myText = myFix.getName();
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    getTemplatePresentation().setText(myText);
    e.getPresentation().setText(myText);
  }

  @Override
  public String getText(RefEntity where) {
    return myText;
  }

  public void setText(final String text) {
    myText = text;
  }


  @Override
  protected boolean isProblemDescriptorsAcceptable() {
    return true;
  }

  public QuickFix getFix() {
    return myFix;
  }

  @Nullable
  protected QuickFix getWorkingQuickFix(@NotNull QuickFix[] fixes) {
    for (QuickFix fix : fixes) {
      if (!myFix.getClass().isInstance(fix)) continue;
      if (myFix instanceof IntentionWrapper && fix instanceof IntentionWrapper &&
          !((IntentionWrapper)myFix).getAction().getClass().isInstance(((IntentionWrapper)fix).getAction())) {
        continue;
      }
      return fix;
    }
    return null;
  }

  @Override
  protected boolean applyFix(RefElement[] refElements) {
    throw new UnsupportedOperationException("");
  }

  @Override
  protected void applyFix(@NotNull final Project project,
                          @NotNull final CommonProblemDescriptor[] descriptors,
                          @NotNull final Set<PsiElement> ignoredElements) {
    final PsiModificationTracker tracker = PsiManager.getInstance(project).getModificationTracker();
    if (myFix instanceof BatchQuickFix) {
      final ArrayList<PsiElement> collectedElementsToIgnore = new ArrayList<PsiElement>();
      final Runnable refreshViews = new Runnable() {
        @Override
        public void run() {
          DaemonCodeAnalyzer.getInstance(project).restart();
          for (CommonProblemDescriptor descriptor : descriptors) {
            ignore(ignoredElements, descriptor, getWorkingQuickFix(descriptor.getFixes()));
          }

          final RefManager refManager = myTool.getContext().getRefManager();
          final RefElement[] refElements = new RefElement[collectedElementsToIgnore.size()];
          for (int i = 0, collectedElementsToIgnoreSize = collectedElementsToIgnore.size(); i < collectedElementsToIgnoreSize; i++) {
            refElements[i] = refManager.getReference(collectedElementsToIgnore.get(i));
          }

          removeElements(refElements, project, myTool);
        }
      };

      ((BatchQuickFix)myFix).applyFix(project, descriptors, collectedElementsToIgnore, refreshViews);
      return;
    }

    boolean restart = false;
    for (CommonProblemDescriptor descriptor : descriptors) {
      if (descriptor == null) continue;
      final QuickFix[] fixes = descriptor.getFixes();
      if (fixes != null) {
        final QuickFix fix = getWorkingQuickFix(fixes);
        if (fix != null) {
          final long startCount = tracker.getModificationCount();
          //CCE here means QuickFix was incorrectly inherited, is there a way to signal (plugin) it is wrong?
          fix.applyFix(project, descriptor);
          if (startCount != tracker.getModificationCount()) {
            restart = true;
            ignore(ignoredElements, descriptor, fix);
          }
        }
      }
    }
    if (restart) {
      DaemonCodeAnalyzer.getInstance(project).restart();
    }
  }

  private void ignore(@NotNull Set<PsiElement> ignoredElements, @NotNull CommonProblemDescriptor descriptor, @Nullable QuickFix fix) {
    if (fix != null) {
      ((DescriptorProviderInspection)myTool).ignoreProblem(descriptor, fix);
    }
    if (descriptor instanceof ProblemDescriptor) {
      ignoredElements.add(((ProblemDescriptor)descriptor).getPsiElement());
    }
  }
}