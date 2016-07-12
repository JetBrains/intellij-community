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
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfile;
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Dmitry Batkovich
 */
public abstract class KeyAwareInspectionViewAction extends InspectionViewActionBase {
  private static final Logger LOG = Logger.getInstance(KeyAwareInspectionViewAction.class);

  public KeyAwareInspectionViewAction(String name) {
    super(name);
  }

  @Override
  protected boolean isEnabled(@NotNull InspectionResultsView view, AnActionEvent e) {
    final InspectionToolWrapper wrapper = view.getTree().getSelectedToolWrapper(true);
    return wrapper != null && HighlightDisplayKey.find(wrapper.getShortName()) != null;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final InspectionResultsView view = getView(e);
    final HighlightDisplayKey key = HighlightDisplayKey.find(view.getTree().getSelectedToolWrapper(true).getShortName());
    actionPerformed(view, key);
  }

  protected abstract void actionPerformed(@NotNull InspectionResultsView view, @NotNull HighlightDisplayKey key);

  public static class DisableInspection extends KeyAwareInspectionViewAction {
    public DisableInspection() {
      super(DisableInspectionToolAction.NAME);
    }

    @Override
    protected boolean isEnabled(@NotNull InspectionResultsView view, AnActionEvent e) {
      final boolean enabled = super.isEnabled(view, e);
      if (!enabled) return false;
      final HighlightDisplayKey key = HighlightDisplayKey.find(view.getTree().getSelectedToolWrapper(true).getShortName());
      final InspectionProfile profile = (InspectionProfile)InspectionProjectProfileManager.getInstance(view.getProject()).getProjectProfileImpl();
      return profile.isToolEnabled(key);
    }

    @Override
    protected void actionPerformed(@NotNull InspectionResultsView view, @NotNull HighlightDisplayKey key) {
      try {
        if (view.isSingleInspectionRun()) {
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
    protected boolean isEnabled(@NotNull InspectionResultsView view, AnActionEvent e) {
      return super.isEnabled(view, e) && getPsiElement(view) != null;
    }

    @Override
    protected void actionPerformed(@NotNull InspectionResultsView view, @NotNull HighlightDisplayKey key) {
      Set<PsiFile> files = new THashSet<>();
      for (RefEntity entity : view.getTree().getSelectedElements()) {
        if (entity instanceof RefElement && entity.isValid()) {
          final PsiElement element = ((RefElement)entity).getElement();
          final PsiFile file = element.getContainingFile();
          files.add(file);
        }
      }

      boolean useModule = true;
      Module module = null;
      for (PsiFile file : files) {
        final Module currentFileModule = ModuleUtilCore.findModuleForPsiElement(file);
        if (currentFileModule != null) {
          if (module == null) {
            module = currentFileModule;
          }
          else if (currentFileModule != module) {
            useModule = false;
            break;
          }
        }
        else {
          useModule = false;
          break;
        }
      }

      final PsiElement context;
      final AnalysisScope scope;
      switch (files.size()) {
        case 0:
          context = null;
          scope = view.getScope();
          break;
        case 1:
          final PsiFile theFile = ContainerUtil.getFirstItem(files);
          LOG.assertTrue(theFile != null);
          context = theFile;
          scope = new AnalysisScope(theFile);
          break;
        default:
          context = null;
          scope = new AnalysisScope(view.getProject(), files.stream().map(PsiFile::getVirtualFile).collect(Collectors.toList()));
      }

      RunInspectionIntention.selectScopeAndRunInspection(key.toString(), scope, useModule ? module : null, context, view.getProject());
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
