// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.hierarchy.HierarchyBrowserBase;
import com.intellij.ide.scratch.ScratchesSearchScope;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.fileEditor.impl.OpenFilesScope;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbUnawareHider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.scope.EditorSelectionLocalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.content.Content;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageView;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.util.PlatformUtils;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

// used by Rider
public abstract class PredefinedSearchScopeProviderBase extends PredefinedSearchScopeProvider {

  public static @NotNull @Nls String getRecentlyViewedFilesScopeName() {
    return IdeBundle.message("scope.recent.files");
  }

  public static @NotNull @Nls String getRecentlyChangedFilesScopeName() {
    return IdeBundle.message("scope.recent.modified.files");
  }

  public static @NotNull @Nls String getCurrentFileScopeName() {
    return IdeBundle.message("scope.current.file");
  }

  private final @NotNull Project myProject;

  protected PredefinedSearchScopeProviderBase(@NotNull Project project) {
    myProject = project;
  }

  protected final @NotNull Project getProject() {
    return myProject;
  }

  protected record ScopeCollectionContext(@Nullable PsiFile psiFile,
                                          @Nullable Editor selectedTextEditor,
                                          @NotNull Collection<? extends SearchScope> scopesFromUsageView,
                                          @Nullable PsiFile currentFile,
                                          @Nullable SearchScope selectedFilesScope) {

    // todo @RequiresBackgroundThread
    @RequiresReadLock
    @NotNull Set<? extends SearchScope> collectRestScopes(@NotNull Project project,
                                                          boolean currentSelection,
                                                          boolean usageView,
                                                          boolean showEmptyScopes) {
      if (project.isDisposed() ||
          psiFile != null && !psiFile.isValid() ||
          selectedTextEditor != null && selectedTextEditor.isDisposed()) {
        return Set.of();
      }

      Set<SearchScope> backgroundResult = new LinkedHashSet<>();
      if (currentFile != null || showEmptyScopes) {
        PsiElement[] scope = currentFile != null ? new PsiElement[]{currentFile} : PsiElement.EMPTY_ARRAY;
        backgroundResult.add(new LocalSearchScope(scope, getCurrentFileScopeName()));
      }

      if (currentSelection && selectedTextEditor != null && psiFile != null) {
        SelectionModel selectionModel = selectedTextEditor.getSelectionModel();
        if (selectionModel.hasSelection()) {
          backgroundResult.add(new EditorSelectionLocalSearchScope(selectedTextEditor, project, IdeBundle.message("scope.selection")));
        }
      }

      if (usageView) {
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.HIERARCHY);
        Content selectedContent = toolWindow != null ? toolWindow.getContentManager().getSelectedContent() : null;
        if (selectedContent != null) {
          addHierarchyScope(selectedContent, backgroundResult);
        }
        backgroundResult.addAll(scopesFromUsageView);
      }

      ContainerUtil.addIfNotNull(backgroundResult, selectedFilesScope);
      return Collections.unmodifiableSet(backgroundResult);
    }
  }

  // todo @RequiresBackgroundThread
  protected final @NotNull Set<? extends SearchScope> getPredefinedScopes(@Nullable DataContext dataContext,
                                                                          boolean suggestSearchInLibs,
                                                                          boolean showEmptyScopes) {
    Set<SearchScope> result = new LinkedHashSet<>();
    result.add(GlobalSearchScope.everythingScope(myProject));
    result.add(GlobalSearchScope.projectScope(myProject));
    if (suggestSearchInLibs) {
      result.add(GlobalSearchScope.allScope(myProject));
    }

    DataContext adjustedContext = dataContext != null ? dataContext : SimpleDataContext.getProjectContext(myProject);
    for (SearchScopeProvider provider : SearchScopeProvider.EP_NAME.getExtensions()) {
      result.addAll(provider.getGeneralSearchScopes(myProject, adjustedContext));
    }

    if (ModuleUtil.hasTestSourceRoots(myProject)) {
      result.add(GlobalSearchScopesCore.projectProductionScope(myProject));
      result.add(GlobalSearchScopesCore.projectTestScope(myProject));
    }

    result.add(ScratchesSearchScope.getScratchesScope(myProject));

    SearchScope recentFilesScope = recentFilesScope(false);
    ContainerUtil.addIfNotNull(
      result, !SearchScope.isEmptyScope(recentFilesScope) ? recentFilesScope :
              showEmptyScopes ? new LocalSearchScope(PsiElement.EMPTY_ARRAY, getRecentlyViewedFilesScopeName()) : null);
    SearchScope recentModFilesScope = recentFilesScope(true);
    ContainerUtil.addIfNotNull(
      result, !SearchScope.isEmptyScope(recentModFilesScope) ? recentModFilesScope :
              showEmptyScopes ? new LocalSearchScope(PsiElement.EMPTY_ARRAY, getRecentlyChangedFilesScopeName()) : null);
    GlobalSearchScope openFilesScope = GlobalSearchScopes.openFilesScope(myProject);
    ContainerUtil.addIfNotNull(
      result, openFilesScope != GlobalSearchScope.EMPTY_SCOPE ? openFilesScope :
              showEmptyScopes ? new LocalSearchScope(PsiElement.EMPTY_ARRAY, OpenFilesScope.getNameText()) : null);

    return Collections.unmodifiableSet(result);
  }

  // todo @RequiresBackgroundThread
  @RequiresReadLock
  protected final @NotNull Set<? extends SearchScope> getScopesFromUsageView(@NotNull UsageView selectedUsageView,
                                                                             boolean prevSearchFiles) {
    if (selectedUsageView.isSearchInProgress()) {
      return Set.of();
    }

    LinkedHashSet<SearchScope> scopes = new LinkedHashSet<>();
    final Set<Usage> usages = new HashSet<>(selectedUsageView.getUsages());
    usages.removeAll(selectedUsageView.getExcludedUsages());

    if (prevSearchFiles) {
      final Set<VirtualFile> files = collectFiles(usages, true);
      if (!files.isEmpty()) {
        GlobalSearchScope prev = new GlobalSearchScope(myProject) {
          private Set<VirtualFile> myFiles;

          @NotNull
          @Override
          public String getDisplayName() {
            return IdeBundle.message("scope.files.in.previous.search.result");
          }

          @Override
          public synchronized boolean contains(@NotNull VirtualFile file) {
            if (myFiles == null) {
              myFiles = collectFiles(usages, false);
            }
            return myFiles.contains(file);
          }

          @Override
          public boolean isSearchInModuleContent(@NotNull Module aModule) {
            return true;
          }

          @Override
          public boolean isSearchInLibraries() {
            return true;
          }
        };
        scopes.add(prev);
      }
    }
    else {
      final List<PsiElement> results = new ArrayList<>(usages.size());
      for (Usage usage : usages) {
        if (usage instanceof PsiElementUsage) {
          final PsiElement element = ((PsiElementUsage)usage).getElement();
          if (element != null && element.isValid() && element.getContainingFile() != null) {
            results.add(element);
          }
        }
      }

      if (!results.isEmpty()) {
        scopes.add(
          new LocalSearchScope(PsiUtilCore.toPsiElementArray(results), IdeBundle.message("scope.previous.search.results")));
      }
    }

    return Collections.unmodifiableSet(scopes);
  }

  // todo @RequiresBackgroundThread
  @RequiresReadLock
  protected static @Nullable PsiFile fillFromDataContext(@Nullable DataContext dataContext,
                                                         @NotNull Collection<? super SearchScope> result,
                                                         @Nullable PsiFile psiFile) {
    PsiFile currentFile = psiFile;
    if (dataContext != null) {
      PsiElement dataContextElement = CommonDataKeys.PSI_FILE.getData(dataContext);
      if (dataContextElement == null) {
        dataContextElement = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
      }

      if (dataContextElement == null && psiFile != null) {
        dataContextElement = psiFile;
      }

      if (dataContextElement != null) {
        if (!PlatformUtils.isCidr() && !PlatformUtils.isRider()) { // TODO: have an API to disable module scopes.
          Module module = findModuleForPsiElement(dataContextElement);
          if (module == null) {
            module = PlatformCoreDataKeys.MODULE.getData(dataContext);
          }
          if (module != null && !ModuleType.isInternal(module)) {
            result.add(module.getModuleScope());
          }
        }
        if (currentFile == null) {
          currentFile = dataContextElement.getContainingFile();
        }
      }
    }
    return currentFile;
  }

  private static @Nullable Module findModuleForPsiElement(@NotNull PsiElement element) {
    return ReadAction.compute(() -> ModuleUtilCore.findModuleForPsiElement(element));
  }

  private static void addHierarchyScope(@NotNull Content content,
                                        @NotNull Collection<? super SearchScope> result) {
    final String name = content.getDisplayName();
    JComponent component = content.getComponent();
    if (component instanceof DumbUnawareHider) {
      component = ((DumbUnawareHider)component).getContent();
    }
    final HierarchyBrowserBase hierarchyBrowserBase = (HierarchyBrowserBase)component;
    final PsiElement[] elements = hierarchyBrowserBase.getAvailableElements();
    if (elements.length > 0) {
      result.add(new LocalSearchScope(elements, LangBundle.message("predefined.search.scope.hearchy.scope.display.name", name)));
    }
  }

  private @NotNull SearchScope recentFilesScope(boolean changedOnly) {
    String name = changedOnly ? getRecentlyChangedFilesScopeName() : getRecentlyViewedFilesScopeName();
    List<VirtualFile> files = changedOnly ?
                              IdeDocumentHistory.getInstance(myProject).getChangedFiles() :
                              JBIterable.from(EditorHistoryManager.getInstance(myProject).getFileList())
                                .append(FileEditorManager.getInstance(myProject).getOpenFiles()).unique().toList();

    return files.isEmpty() ? LocalSearchScope.EMPTY : GlobalSearchScope.filesScope(myProject, files, name);
  }

  // todo @RequiresBackgroundThread
  @RequiresReadLock
  public @Nullable SearchScope getSelectedFilesScope(@Nullable DataContext dataContext,
                                                     @Nullable PsiFile currentFile) {
    VirtualFile[] filesOrDirs = dataContext == null ? null : CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
    if (filesOrDirs == null ||
        filesOrDirs.length == 0 ||
        filesOrDirs.length == 1 && currentFile != null && filesOrDirs[0].equals(currentFile.getVirtualFile())) {
      return null;
    }
    return new SelectedFilesScope(filesOrDirs);
  }

  protected static @NotNull Set<VirtualFile> collectFiles(@NotNull Set<? extends Usage> usages,
                                                          boolean findFirst) {
    final Set<VirtualFile> files = new HashSet<>();
    for (Usage usage : usages) {
      if (usage instanceof PsiElementUsage) {
        PsiElement psiElement = ((PsiElementUsage)usage).getElement();
        if (psiElement != null && psiElement.isValid()) {
          PsiFile psiFile = psiElement.getContainingFile();
          if (psiFile != null) {
            VirtualFile file = psiFile.getVirtualFile();
            if (file != null) {
              files.add(file);
              if (findFirst) return files;
            }
          }
        }
      }
    }
    return files;
  }

  final class SelectedFilesScope extends GlobalSearchScope {
    private final Set<VirtualFile> myFiles = new HashSet<>();
    private final Set<VirtualFile> myDirectories = new HashSet<>();

    SelectedFilesScope(VirtualFile... filesOrDirs) {
      super(myProject);

      if (filesOrDirs.length == 0) {
        throw new IllegalArgumentException("array is empty");
      }
      for (VirtualFile fileOrDir : filesOrDirs) {
        if (fileOrDir.isDirectory()) {
          myDirectories.add(fileOrDir);
        }
        else {
          myFiles.add(fileOrDir);
        }
      }
    }

    @Override
    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return true;
    }

    @Override
    public boolean isSearchInLibraries() {
      return true;
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      for (VirtualFile virtualFile : myFiles) {
        if (virtualFile.equals(file)) {
          return true;
        }
      }
      return VfsUtilCore.isUnder(file, myDirectories);
    }

    @Override
    public @NotNull @Nls String getDisplayName() {
      if (myFiles.isEmpty()) {
        return IdeBundle.message("scope.selected.directories", myDirectories.size());
      }
      if (myDirectories.isEmpty()) {
        return IdeBundle.message("scope.selected.files", myFiles.size());
      }
      return IdeBundle.message("scope.selected.files.and.directories", myFiles.size(), myDirectories.size());
    }
  }
}