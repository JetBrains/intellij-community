// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.classpath;

import com.intellij.CommonBundle;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.scopes.LibraryScope;
import com.intellij.openapi.roots.*;
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
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

class AnalyzeModuleDependencyAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(AnalyzeModuleDependencyAction.class);
  private final ClasspathPanel myPanel;

  AnalyzeModuleDependencyAction(final ClasspathPanel panel) {
    super(JavaUiBundle.message("action.text.analyze.this.dependency"));
    myPanel = panel;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final OrderEntry selectedEntry = myPanel.getSelectedEntry();
    GlobalSearchScope mainScope = getScopeForOrderEntry(selectedEntry);
    LOG.assertTrue(mainScope != null);
    Map<GlobalSearchScope, OrderEntry> additionalScopes;
    ModulesProvider modulesProvider = myPanel.getModuleConfigurationState().getModulesProvider();
    if (selectedEntry instanceof ModuleOrderEntry) {
      Module depModule = ((ModuleOrderEntry)selectedEntry).getModule();
      LOG.assertTrue(depModule != null);
      Map<OrderEntry, OrderEntry> additionalDependencies = JavaProjectRootsUtil
        .findExportedDependenciesReachableViaThisDependencyOnly(myPanel.getRootModel().getModule(),
                                                                depModule, modulesProvider);
      additionalScopes = new LinkedHashMap<>();
      for (Map.Entry<OrderEntry, OrderEntry> entry : additionalDependencies.entrySet()) {
        additionalScopes.put(getScopeForOrderEntry(entry.getKey()), entry.getValue());
      }
    }
    else {
      additionalScopes = Collections.emptyMap();
    }

    List<GlobalSearchScope> scopes = new ArrayList<>(additionalScopes.keySet());
    scopes.add(mainScope);
    new AnalyzeDependenciesOnSpecifiedTargetHandler(myPanel.getProject(), new AnalysisScope(myPanel.getModuleConfigurationState().getCurrentRootModel().getModule()),
                                                    GlobalSearchScope.union(scopes.toArray(GlobalSearchScope.EMPTY_ARRAY))) {
      @Override
      protected boolean shouldShowDependenciesPanel(List<? extends DependenciesBuilder> builders) {
        Set<GlobalSearchScope> usedScopes = findUsedScopes(builders, scopes);
        if (usedScopes.contains(mainScope)) {
          Messages.showInfoMessage(myProject,
                                   JavaUiBundle
                                     .message("message.text.dependencies.were.successfully.collected.in.0.toolwindow", ToolWindowId.DEPENDENCIES),
                                   getTemplateText());
          return true;
        }

        List<OrderEntry> usedEntries = usedScopes.stream().map(additionalScopes::get).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (usedEntries.isEmpty()) {
          final String message = JavaUiBundle.message("analyze.module.dependency.action.dialog.message.no.dependency.found", generateSkipImportsWarning());
          if (Messages.showOkCancelDialog(myProject, message, getTemplateText(), CommonBundle.message("button.remove"),
                                          Messages.getCancelButton(),
                                          Messages.getWarningIcon()) == Messages.OK) {
            myPanel.getRootModel().removeOrderEntry(selectedEntry);
          }
          return false;
        }

        final String firstEntry = usedEntries.get(0).getPresentableName();
        final List<OrderEntry> tailEntries;
        final String secondEntry;
        if (usedEntries.size() > 1) {
          tailEntries = usedEntries.subList(1, usedEntries.size() - 1);
          secondEntry = usedEntries.get(1).getPresentableName();
        }
        else {
          tailEntries = Collections.emptyList();
          secondEntry = null;
        }

        final String howeverExportedBy = JavaUiBundle.message("analyze.module.dependency.however.exported.by",
                                                    firstEntry,
                                                    secondEntry,
                                                    StringUtil.decapitalize(selectedEntry.getPresentableName()),
                                                    tailEntries.size(),
                                                    usedEntries.size());
        final String confirmReplace = JavaUiBundle.message("analyze.module.dependency.replace.dialog.confirm.replace",
                                                    selectedEntry.getPresentableName(),
                                                    usedEntries.size(),
                                                    firstEntry);
        final String message = JavaUiBundle.message("analyze.module.dependency.replace.dialog.message",
                                                    generateSkipImportsWarning(),
                                                    howeverExportedBy, confirmReplace
                                                    );

        String[] options = {JavaUiBundle.message("button.text.replace"), JavaUiBundle.message("show.dependencies"), Messages.getCancelButton()};
        switch (Messages.showDialog(myProject, message, getTemplateText(), options, 0, Messages.getWarningIcon())) {
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

  private @Nls(capitalization = Nls.Capitalization.Sentence) String generateSkipImportsWarning() {
    if (DependencyVisitorFactory.VisitorOptions.fromSettings(myPanel.getProject()).skipImports()) {
      return " " + CodeInsightBundle.message("dependencies.in.imports.message");
    }
    return "";
  }

  private static Set<GlobalSearchScope> findUsedScopes(List<? extends DependenciesBuilder> builders, List<? extends GlobalSearchScope> scopes) {
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
    if (selectedEntry instanceof ModuleSourceOrderEntry) {
      return GlobalSearchScope.moduleScope(selectedEntry.getOwnerModule());
    }
    if (selectedEntry instanceof ModuleOrderEntry) {
      Module module = ((ModuleOrderEntry)selectedEntry).getModule();
      return module != null ? GlobalSearchScope.moduleScope(module) : null;
    }
    if (selectedEntry instanceof LibraryOrderEntry) {
      Library library = ((LibraryOrderEntry)selectedEntry).getLibrary();
      return library != null ? new LibraryScope(myPanel.getProject(), library) : null;
    }
    return null;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final OrderEntry entry = myPanel.getSelectedEntry();
    e.getPresentation().setVisible(entry instanceof ModuleOrderEntry && ((ModuleOrderEntry)entry).getModule() != null
                                 || entry instanceof LibraryOrderEntry && ((LibraryOrderEntry)entry).getLibrary() != null);
  }
}
