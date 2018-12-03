// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.classpath;

import com.intellij.CommonBundle;
import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.find.FindBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.scopes.LibraryScope;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.packageDependencies.DependencyVisitorFactory;
import com.intellij.packageDependencies.actions.AnalyzeDependenciesOnSpecifiedTargetHandler;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

class AnalyzeModuleDependencyAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(AnalyzeModuleDependencyAction.class);
  private final ClasspathPanel myPanel;

  AnalyzeModuleDependencyAction(final ClasspathPanel panel) {
    super("Analyze This Dependency");
    this.myPanel = panel;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final OrderEntry selectedEntry = myPanel.getSelectedEntry();
    GlobalSearchScope targetScope;
    if (selectedEntry instanceof ModuleOrderEntry) {
      final Module module = ((ModuleOrderEntry)selectedEntry).getModule();
      LOG.assertTrue(module != null);
      targetScope = GlobalSearchScope.moduleScope(module);
    }
    else {
      Library library = ((LibraryOrderEntry)selectedEntry).getLibrary();
      LOG.assertTrue(library != null);
      targetScope = new LibraryScope(myPanel.getProject(), library);
    }
    new AnalyzeDependenciesOnSpecifiedTargetHandler(myPanel.getProject(), new AnalysisScope(myPanel.getModuleConfigurationState().getRootModel().getModule()),
                                                    targetScope) {
      @Override
      protected boolean shouldShowDependenciesPanel(List<? extends DependenciesBuilder> builders) {
        for (DependenciesBuilder builder : builders) {
          for (Set<PsiFile> files : builder.getDependencies().values()) {
            if (!files.isEmpty()) {
              Messages.showInfoMessage(myProject,
                                       "Dependencies were successfully collected in \"" +
                                       ToolWindowId.DEPENDENCIES + "\" toolwindow",
                                       FindBundle.message("find.pointcut.applications.not.found.title"));
              return true;
            }
          }
        }
        String message = "No code dependencies were found.";
        if (DependencyVisitorFactory.VisitorOptions.fromSettings(myProject).skipImports()) {
          message += " ";
          message += AnalysisScopeBundle.message("dependencies.in.imports.message");
        }
        message += " Would you like to remove the dependency?";
        if (Messages.showOkCancelDialog(myProject, message, CommonBundle.getWarningTitle(), CommonBundle.message("button.remove"), Messages.CANCEL_BUTTON,
                                        Messages.getWarningIcon()) == Messages.OK) {
          myPanel.getRootModel().removeOrderEntry(selectedEntry);
        }
        return false;
      }

      @Override
      protected boolean canStartInBackground() {
        return false;
      }
    }.analyze();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final OrderEntry entry = myPanel.getSelectedEntry();
    e.getPresentation().setVisible(entry instanceof ModuleOrderEntry && ((ModuleOrderEntry)entry).getModule() != null
                                 || entry instanceof LibraryOrderEntry && ((LibraryOrderEntry)entry).getLibrary() != null);
  }
}
