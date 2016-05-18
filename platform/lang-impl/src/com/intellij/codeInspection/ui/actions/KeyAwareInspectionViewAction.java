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
package com.intellij.codeInspection.ui.actions;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ModifiableModel;
import com.intellij.codeInspection.actions.RunInspectionIntention;
import com.intellij.codeInspection.ex.DisableInspectionToolAction;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public abstract class KeyAwareInspectionViewAction extends InspectionViewActionBase {
  public KeyAwareInspectionViewAction(String name) {
    super(name);
  }

  @Override
  protected boolean isEnabled(@NotNull InspectionResultsView view) {
    final InspectionToolWrapper wrapper = view.getTree().getSelectedToolWrapper();
    return wrapper != null && HighlightDisplayKey.find(wrapper.getShortName()) != null;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final InspectionResultsView view = getView(e);
    final HighlightDisplayKey key = HighlightDisplayKey.find(view.getTree().getSelectedToolWrapper().getShortName());
    actionPerformed(view, key);
  }

  protected abstract void actionPerformed(@NotNull InspectionResultsView view, @NotNull HighlightDisplayKey key);

  public static class DisableInspection extends KeyAwareInspectionViewAction {
    public DisableInspection() {
      super(DisableInspectionToolAction.NAME);
    }

    @Override
    protected void actionPerformed(@NotNull InspectionResultsView view, @NotNull HighlightDisplayKey key) {
      try {
        if (view.isProfileDefined()) {
          final ModifiableModel model = view.getCurrentProfile().getModifiableModel();
          model.disableTool(key.toString(), view.getProject());
          model.commit();
          view.updateCurrentProfile();
        } else {
          final RefEntity[] selectedElements = view.getTree().getSelectedElements();
          final Set<PsiElement> files = new HashSet<PsiElement>();
          final Project project = view.getProject();
          final InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(project);
          for (RefEntity selectedElement : selectedElements) {
            if (selectedElement instanceof RefElement) {
              final PsiElement element = ((RefElement)selectedElement).getElement();
              files.add(element);
            }
          }
          ModifiableModel model = ((InspectionProfileImpl)profileManager.getProjectProfileImpl()).getModifiableModel();
          for (PsiElement element : files) {
            model.disableTool(key.toString(), element);
          }
          model.commit();
          DaemonCodeAnalyzer.getInstance(project).restart();
        }
      }
      catch (IOException e1) {
        Messages.showErrorDialog(view.getProject(), e1.getMessage(), CommonBundle.getErrorTitle());
      }
    }
  }

  public static class RunInspectionOn extends KeyAwareInspectionViewAction {
    public RunInspectionOn() {
      super(InspectionsBundle.message("run.inspection.on.file.intention.text"));
    }

    @Override
    protected boolean isEnabled(@NotNull InspectionResultsView view) {
      return super.isEnabled(view) && getPsiElement(view) != null;
    }

    @Override
    protected void actionPerformed(@NotNull InspectionResultsView view, @NotNull HighlightDisplayKey key) {
      final PsiElement psiElement = getPsiElement(view);
      assert psiElement != null;
      new RunInspectionIntention(key).invoke(view.getProject(), null, psiElement.getContainingFile());
    }

    @Nullable
    private static PsiElement getPsiElement(InspectionResultsView view) {
      final RefEntity[] selectedElements = view.getTree().getSelectedElements();

      final PsiElement psiElement;
      if (selectedElements.length > 0 && selectedElements[0] instanceof RefElement) {
        psiElement = ((RefElement)selectedElements[0]).getElement();
      }
      else {
        psiElement = null;
      }
      return psiElement;
    }
  }
}
