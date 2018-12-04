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
import com.intellij.openapi.module.impl.scopes.ModuleWithDependenciesScope;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.impl.OrderEntryUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.packageDependencies.DependencyVisitorFactory;
import com.intellij.packageDependencies.actions.AnalyzeDependenciesOnSpecifiedTargetHandler;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.roots.JavaProjectRootsUtil.findExportedDependenciesReachableViaThisDependencyOnly;

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
    GlobalSearchScope mainScope = getScopeForOrderEntry(selectedEntry);
    LOG.assertTrue(mainScope != null);
    List<GlobalSearchScope> additionalScopes;
    ModulesProvider modulesProvider = myPanel.getModuleConfigurationState().getModulesProvider();
    if (selectedEntry instanceof ModuleOrderEntry) {
      Module depModule = ((ModuleOrderEntry)selectedEntry).getModule();
      LOG.assertTrue(depModule != null);
      List<OrderEntry> additionalDependencies = findExportedDependenciesReachableViaThisDependencyOnly(myPanel.getRootModel().getModule(),
                                                                                                       depModule, modulesProvider);
      additionalScopes = ContainerUtil.mapNotNull(additionalDependencies, this::getScopeForOrderEntry);
    }
    else {
      additionalScopes = Collections.emptyList();
    }

    GlobalSearchScope[] scopes = ContainerUtil.append(additionalScopes, mainScope).toArray(GlobalSearchScope.EMPTY_ARRAY);
    new AnalyzeDependenciesOnSpecifiedTargetHandler(myPanel.getProject(), new AnalysisScope(myPanel.getModuleConfigurationState().getRootModel().getModule()),
                                                    GlobalSearchScope.union(scopes)) {
      @Override
      protected boolean shouldShowDependenciesPanel(List<? extends DependenciesBuilder> builders) {
        Set<GlobalSearchScope> usedScopes = findUsedScopes(builders, scopes);
        if (usedScopes.contains(mainScope)) {
          Messages.showInfoMessage(myProject,
                                   "Dependencies were successfully collected in \"" +
                                   ToolWindowId.DEPENDENCIES + "\" toolwindow",
                                   FindBundle.message("find.pointcut.applications.not.found.title"));
          return true;
        }

        if (usedScopes.isEmpty()) {
          String message = "No code dependencies were found." + generateSkipImportsWarning() + " Would you like to remove the dependency?";
          if (Messages.showOkCancelDialog(myProject, message, CommonBundle.getWarningTitle(), CommonBundle.message("button.remove"), Messages.CANCEL_BUTTON,
                                          Messages.getWarningIcon()) == Messages.OK) {
            myPanel.getRootModel().removeOrderEntry(selectedEntry);
          }
          return false;
        }

        LOG.assertTrue(selectedEntry instanceof ModuleOrderEntry);
        Module depModule = ((ModuleOrderEntry)selectedEntry).getModule();
        LOG.assertTrue(depModule != null);
        List<OrderEntry> usedEntries = ContainerUtil.map(usedScopes, scope -> findOrderEntryByScope(scope, modulesProvider.getRootModel(depModule)));
        String usedExportedEntriesText = "'" + usedEntries.get(0).getPresentableName() + "'";
        if (usedEntries.size() == 2) {
          usedExportedEntriesText += " and '" + usedEntries.get(1).getPresentableName() + "'";
        }
        else if (usedEntries.size() > 2) {
          usedExportedEntriesText += " and " + (usedEntries.size() - 1) + " more dependencies";
        }

        String replacementText = usedEntries.size() == 1 ? "a direct dependency on '" + usedEntries.get(0).getPresentableName() + "'" : "direct dependencies";
        String message = "No direct code dependencies were found." + generateSkipImportsWarning() + "\nHowever " + usedExportedEntriesText + " exported by '"
                         + StringUtil.decapitalize(selectedEntry.getPresentableName()) + "' " + (usedEntries.size() > 1 ? "are" : "is") + " used in code.\n" +
                         "Do you want to replace dependency on '" + selectedEntry.getPresentableName() + "' by " + replacementText + "?";
        String[] options = {"Replace", "Show Dependencies", Messages.CANCEL_BUTTON};
        switch (Messages.showDialog(myProject, message, CommonBundle.getWarningTitle(), options, 0, Messages.getWarningIcon())) {
          case 0:
            InlineModuleDependencyAction.inlineEntry(myPanel, selectedEntry, usedEntries::contains);
            return false;
          case 1:
            return true;
          default:
            return false;
        }
      }

      @Override
      protected boolean canStartInBackground() {
        return false;
      }
    }.analyze();
  }

  private String generateSkipImportsWarning() {
    if (DependencyVisitorFactory.VisitorOptions.fromSettings(myPanel.getProject()).skipImports()) {
      return " " + AnalysisScopeBundle.message("dependencies.in.imports.message");
    }
    return "";
  }

  private static Set<GlobalSearchScope> findUsedScopes(List<? extends DependenciesBuilder> builders, GlobalSearchScope[] scopes) {
    Set<GlobalSearchScope> usedScopes = new LinkedHashSet<>();
    for (DependenciesBuilder builder : builders) {
      for (Set<PsiFile> files : builder.getDependencies().values()) {
        for (PsiFile file : files) {
          VirtualFile virtualFile = file.getVirtualFile();
          if (virtualFile != null) {
            for (GlobalSearchScope scope : scopes) {
              if (scope.contains(virtualFile)) {
                usedScopes.add(scope);
              }
            }
          }
        }
      }
    }
    return usedScopes;
  }

  @Contract("null -> null")
  private GlobalSearchScope getScopeForOrderEntry(OrderEntry selectedEntry) {
    if (selectedEntry instanceof ModuleOrderEntry) {
      Module module = ((ModuleOrderEntry)selectedEntry).getModule();
      return module != null ? GlobalSearchScope.moduleScope(module) : null;
    }
    else if (selectedEntry instanceof LibraryOrderEntry) {
      Library library = ((LibraryOrderEntry)selectedEntry).getLibrary();
      return library != null ? new LibraryScope(myPanel.getProject(), library) : null;
    }
    return null;
  }

  private static OrderEntry findOrderEntryByScope(@NotNull GlobalSearchScope scope, ModuleRootModel rootModel) {
    if (scope instanceof ModuleWithDependenciesScope) {
      Module module = ((ModuleWithDependenciesScope)scope).getModule();
      return OrderEntryUtil.findModuleOrderEntry(rootModel, module);
    }
    else if (scope instanceof LibraryScope) {
      Library library = ((LibraryScope)scope).getLibrary();
      return OrderEntryUtil.findLibraryOrderEntry(rootModel, library);
    }
    LOG.error("Cannot find order entry by " + scope + " in " + rootModel);
    return null;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final OrderEntry entry = myPanel.getSelectedEntry();
    e.getPresentation().setVisible(entry instanceof ModuleOrderEntry && ((ModuleOrderEntry)entry).getModule() != null
                                 || entry instanceof LibraryOrderEntry && ((LibraryOrderEntry)entry).getLibrary() != null);
  }
}
