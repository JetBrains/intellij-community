/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection.ui.actions.occurrences;

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ProblemDescriptorBase;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Dmitry Batkovich
 */
public class GoToSubsequentOccurrenceAction extends AnAction {
  private final boolean myNext;
  private OccurrencesManager myManager;

  private GoToSubsequentOccurrenceAction(boolean next, OccurrencesManager manager) {
    myNext = next;
    myManager = manager;
    getTemplatePresentation().setIcon(next ? AllIcons.Actions.NextOccurence : AllIcons.Actions.PreviousOccurence);
    getTemplatePresentation().setDescription("Navigate to the " + (next ? "Next" : "Previous") + " Occurrence");
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(myManager.hasValidSubsequent());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    myManager.selectSubsequent(myNext);
  }

  public static ActionGroup createNextPreviousActions(@NotNull Editor editor, @NotNull List<CommonProblemDescriptor> descriptors) {
    OccurrencesManager manager = new OccurrencesManager(editor, descriptors);
    return new DefaultActionGroup(new GoToSubsequentOccurrenceAction(false, manager), new GoToSubsequentOccurrenceAction(true, manager));
  }

  private static class OccurrencesManager {
    private final Editor myEditor;
    private final List<ProblemDescriptorBase> myChildren;

    private int mySelected = -1;

    private OccurrencesManager(@NotNull Editor editor, @NotNull List<CommonProblemDescriptor> descriptors) {
      myEditor = editor;
      myChildren = descriptors.stream()
        .filter(d -> d instanceof ProblemDescriptorBase)
        .map(d -> (ProblemDescriptorBase) d)
        .filter(d -> {
          PsiElement element = d.getPsiElement();
          return element != null && element.isValid();
        })
        .sorted((o1, o2) -> o1.getPsiElement().getTextOffset() - o2.getPsiElement().getTextOffset())
        .collect(Collectors.toList());
      selectSubsequent(true);
    }

    public void selectSubsequent(boolean next) {
      int subsequentIdz = mySelected;
      while (true) {
        subsequentIdz += next ?  1 : -1;
        subsequentIdz += myChildren.size();
        subsequentIdz %= myChildren.size();
        if (mySelected == subsequentIdz) {
          throw new IllegalStateException("Selection is unavailable");
        }
        ProblemDescriptorBase descriptorBase = myChildren.get(subsequentIdz);
        if (descriptorBase.getPsiElement().isValid()) {
          mySelected = subsequentIdz;
          PsiElement toSelect = descriptorBase.getPsiElement();
          myEditor.getCaretModel().moveToOffset(toSelect.getTextOffset());
          myEditor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
          myEditor.getSelectionModel().setSelection(toSelect.getTextRange().getStartOffset(), toSelect.getTextRange().getEndOffset());
          return;
        }
      }
    }

    public boolean hasValidSubsequent() {
      int subsequentIdz = mySelected;
      while (true) {
        subsequentIdz += 1 + myChildren.size();
        subsequentIdz %= myChildren.size();
        if (mySelected == subsequentIdz) {
          return false;
        }
        ProblemDescriptorBase descriptorBase = myChildren.get(subsequentIdz);
        if (descriptorBase.getPsiElement().isValid()) {
          return true;
        }
      }
    }
  }
}
