// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.ProjectViewSettings;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ui.configuration.ModuleSourceRootEditHandler;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.FontUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;

import java.util.*;
import java.util.stream.Collectors;

public class ProjectViewDirectoryHelper {
  protected static final Logger LOG = Logger.getInstance(ProjectViewDirectoryHelper.class);

  private final Project myProject;
  private final ProjectFileIndex myFileIndex;

  public static ProjectViewDirectoryHelper getInstance(@NotNull Project project) {
    return project.getService(ProjectViewDirectoryHelper.class);
  }

  public ProjectViewDirectoryHelper(Project project) {
    myProject = project;
    myFileIndex = ProjectFileIndex.getInstance(project);
  }

  public Project getProject() {
    return myProject;
  }


  public @Nullable String getLocationString(@NotNull PsiDirectory psiDirectory) {
    boolean includeUrl = ProjectRootsUtil.isModuleContentRoot(psiDirectory);
    return getLocationString(psiDirectory, includeUrl, false);
  }

  public @Nullable String getLocationString(@NotNull PsiDirectory psiDirectory, boolean includeUrl, boolean includeRootType) {
    StringBuilder result = new StringBuilder();

    final VirtualFile directory = psiDirectory.getVirtualFile();

    if (ProjectRootsUtil.isLibraryRoot(directory, psiDirectory.getProject())) {
      result.append(ProjectBundle.message("module.paths.root.node", "library").toLowerCase(Locale.getDefault()));
    }
    else if (includeRootType) {
      SourceFolder sourceRoot = ProjectRootsUtil.getModuleSourceRoot(psiDirectory.getVirtualFile(), psiDirectory.getProject());
      if (sourceRoot != null) {
        ModuleSourceRootEditHandler<?> handler = ModuleSourceRootEditHandler.getEditHandler(sourceRoot.getRootType());
        if (handler != null) {
          JavaSourceRootProperties properties = sourceRoot.getJpsElement().getProperties(JavaModuleSourceRootTypes.SOURCES);
          if (properties != null && properties.isForGeneratedSources()) {
            result.append("generated ");
          }
          result.append(handler.getFullRootTypeName().toLowerCase(Locale.getDefault()));
        }
      }
    }

    if (includeUrl) {
      if (result.length() > 0) result.append(",").append(FontUtil.spaceAndThinSpace());
      result.append(FileUtil.getLocationRelativeToUserHome(directory.getPresentableUrl()));
    }

    return result.length() == 0 ? null : result.toString();
  }


  public boolean isShowFQName(ViewSettings settings, Object parentValue, PsiDirectory value) {
    return false;
  }

  /**
   * Returns {@code true} if the directory containing project configuration files (.idea) should be hidden in Project View.
   */
  public boolean shouldHideProjectConfigurationFilesDirectory() {
    return true;
  }

  public @Nullable String getNodeName(ViewSettings settings, Object parentValue, PsiDirectory directory) {
    return directory.getName();
  }

  public boolean skipDirectory(PsiDirectory directory) {
    return true;
  }

  public boolean isEmptyMiddleDirectory(PsiDirectory directory, final boolean strictlyEmpty) {
    return isEmptyMiddleDirectory(directory, strictlyEmpty, null);
  }

  public boolean isEmptyMiddleDirectory(PsiDirectory directory,
                                        final boolean strictlyEmpty,
                                        @Nullable PsiFileSystemItemFilter filter) {
    return false;
  }

  public boolean supportsFlattenPackages() {
    return false;
  }

  public boolean supportsHideEmptyMiddlePackages() {
    return false;
  }

  public boolean canRepresent(Object element, PsiDirectory directory) {
    if (element instanceof VirtualFile vFile) {
      return Comparing.equal(directory.getVirtualFile(), vFile);
    }
    return false;
  }

  public boolean canRepresent(@NotNull VirtualFile element, @NotNull PsiDirectory directory, @NotNull PsiDirectory owner, ViewSettings settings) {
    if (canRepresent(element, directory)) return true;
    if (settings == null) return false; // unexpected
    if (settings.isFlattenPackages() || !settings.isHideEmptyMiddlePackages()) {
      return false;
    }
    return !processParents(directory, owner, dir -> !element.equals(dir.getVirtualFile()));
  }

  boolean isValidDirectory(PsiDirectory directory, Object owner, ViewSettings settings, PsiFileSystemItemFilter filter) {
    if (directory == null || !directory.isValid()) return false;
    if (settings == null) return true; // unexpected
    if (!(owner instanceof PsiDirectory)) return true;
    if (!settings.isFlattenPackages() && settings.isHideEmptyMiddlePackages()) {
      PsiDirectory parent = directory.getParent();
      if (parent == null || skipDirectory(parent)) return true;
      if (ProjectRootsUtil.isSourceRoot(directory)) return true;
      if (isEmptyMiddleDirectory(directory, true, filter)) return false;
      return processParents(directory, (PsiDirectory)owner, dir -> {
        if (!dir.isValid()) return false;
        PsiDirectory dirParent = dir.getParent();
        if (dirParent == null || skipDirectory(dirParent)) return false;
        return isEmptyMiddleDirectory(dir, true, filter);
      });
    }
    return true;
  }

  private static boolean processParents(@NotNull PsiDirectory directory, @NotNull PsiDirectory owner,
                                        @NotNull Processor<? super PsiDirectory> processor) {
    for (directory = directory.getParentDirectory();
         directory != null && !directory.equals(owner);
         directory = directory.getParentDirectory()) {
      if (!processor.process(directory)) return false;
    }
    return true;
  }

  public @NotNull Collection<AbstractTreeNode<?>> getDirectoryChildren(final PsiDirectory psiDirectory,
                                                                       final ViewSettings settings,
                                                                       final boolean withSubDirectories) {
    return getDirectoryChildren(psiDirectory, settings, withSubDirectories, null);
  }

  public @NotNull Collection<AbstractTreeNode<?>> getDirectoryChildren(PsiDirectory psiDirectory,
                                                                       ViewSettings settings,
                                                                       boolean withSubDirectories,
                                                                       @Nullable PsiFileSystemItemFilter filter) {
    List<AbstractTreeNode<?>> children = new ArrayList<>();
    if (!psiDirectory.isValid()) return children;
    Project project = psiDirectory.getProject();
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    Module module = fileIndex.getModuleForFile(psiDirectory.getVirtualFile());
    ModuleFileIndex moduleFileIndex = module == null ? null : ModuleRootManager.getInstance(module).getFileIndex();
    if (!settings.isFlattenPackages() || skipDirectory(psiDirectory)) {
      processPsiDirectoryChildren(psiDirectory, directoryChildrenInProject(psiDirectory, settings),
                                  children, fileIndex, null, settings, withSubDirectories, filter);
    }
    else { // source directory in "flatten packages" mode
      final PsiDirectory parentDir = psiDirectory.getParentDirectory();
      if (parentDir == null || skipDirectory(parentDir) && withSubDirectories) {
        addAllSubpackages(children, psiDirectory, moduleFileIndex, settings, filter);
      }
      if (withSubDirectories) {
        PsiDirectory[] subdirs = psiDirectory.getSubdirectories();
        for (PsiDirectory subdir : subdirs) {
          if (!skipDirectory(subdir) || filter != null && !filter.shouldShow(subdir)) {
            continue;
          }
          VirtualFile directoryFile = subdir.getVirtualFile();

          if (Registry.is("ide.hide.excluded.files")) {
            if (fileIndex.isExcluded(directoryFile)) continue;
          }
          else {
            if (FileTypeRegistry.getInstance().isFileIgnored(directoryFile)) continue;
          }

          children.add(new PsiDirectoryNode(project, subdir, settings, filter));
        }
      }
      processPsiDirectoryChildren(psiDirectory, psiDirectory.getFiles(), children, fileIndex, moduleFileIndex, settings,
                                  withSubDirectories, filter);
    }
    return children;
  }

  public @NotNull List<VirtualFile> getTopLevelRoots() {
    List<VirtualFile> topLevelContentRoots = new ArrayList<>();
    ProjectRootManager prm = ProjectRootManager.getInstance(myProject);

    for (VirtualFile root : prm.getContentRoots()) {
      VirtualFile parent = root.getParent();
      if (!isFileUnderContentRoot(parent)) {
        topLevelContentRoots.add(root);
      }
    }
    Collection<UnloadedModuleDescription> descriptions = ModuleManager.getInstance(myProject).getUnloadedModuleDescriptions();
    for (UnloadedModuleDescription description : descriptions) {
      for (VirtualFilePointer pointer : description.getContentRoots()) {
        VirtualFile root = pointer.getFile();
        if (root != null) {
          VirtualFile parent = root.getParent();
          if (!isFileUnderContentRoot(parent)) {
            topLevelContentRoots.add(root);
          }
        }
      }
    }
    return topLevelContentRoots;
  }

  @NotNull
  List<VirtualFile> getTopLevelModuleRoots(Module module, ViewSettings settings) {
    return ContainerUtil.filter(ModuleRootManager.getInstance(module).getContentRoots(), root -> {
      if (!shouldBeShown(root, settings)) return false;
      VirtualFile parent = root.getParent();
      if (parent == null) return true;
      if (!module.equals(myFileIndex.getModuleForFile(parent, false))) return true;
      //show inner content root separately only if it won't be shown under outer content root
      return myFileIndex.isExcluded(parent) && !shouldShowExcludedFiles(settings);
    });
  }

  @NotNull
  List<VirtualFile> getTopLevelUnloadedModuleRoots(UnloadedModuleDescription module, ViewSettings settings) {
    return module.getContentRoots().stream()
      .map(VirtualFilePointer::getFile)
      .filter(root -> root != null && shouldBeShown(root, settings))
      .collect(Collectors.toList());
  }


  private boolean isFileUnderContentRoot(@Nullable VirtualFile file) {
    return file != null && file.isValid() && myFileIndex.getContentRootForFile(file, false) != null;
  }

  private PsiElement @NotNull [] directoryChildrenInProject(PsiDirectory psiDirectory, final ViewSettings settings) {
    final VirtualFile dir = psiDirectory.getVirtualFile();
    if (shouldBeShown(dir, settings)) {
      final List<PsiElement> children = new ArrayList<>();
      psiDirectory.processChildren(new PsiElementProcessor<>() {
        @Override
        public boolean execute(@NotNull PsiFileSystemItem element) {
          if (shouldBeShown(element.getVirtualFile(), settings)) {
            children.add(element);
          }
          return true;
        }
      });
      return PsiUtilCore.toPsiElementArray(children);
    }

    PsiManager manager = psiDirectory.getManager();
    Set<PsiElement> directoriesOnTheWayToContentRoots = new HashSet<>();
    for (VirtualFile root : getTopLevelRoots()) {
      VirtualFile current = root;
      while (current != null) {
        VirtualFile parent = current.getParent();

        if (Comparing.equal(parent, dir)) {
          final PsiDirectory psi = manager.findDirectory(current);
          if (psi != null) {
            directoriesOnTheWayToContentRoots.add(psi);
          }
        }
        current = parent;
      }
    }

    return PsiUtilCore.toPsiElementArray(directoriesOnTheWayToContentRoots);
  }

  private boolean shouldBeShown(@NotNull VirtualFile dir, ViewSettings settings) {
    if (!dir.isValid()) return false;
    return shouldShowExcludedFiles(settings)
           ? myFileIndex.isInProjectOrExcluded(dir)
           : myFileIndex.isInProject(dir) && !ProjectUtil.isProjectOrWorkspaceFile(dir);
  }

  private static boolean shouldShowExcludedFiles(ViewSettings settings) {
    return !Registry.is("ide.hide.excluded.files") && settings instanceof ProjectViewSettings && ((ProjectViewSettings)settings).isShowExcludedFiles();
  }

  // used only for non-flatten packages mode
  private void processPsiDirectoryChildren(final PsiDirectory psiDir,
                                           PsiElement[] children,
                                           List<? super AbstractTreeNode<?>> container,
                                           ProjectFileIndex projectFileIndex,
                                           @Nullable ModuleFileIndex moduleFileIndex,
                                           ViewSettings viewSettings,
                                           boolean withSubDirectories,
                                           @Nullable PsiFileSystemItemFilter filter) {
    for (PsiElement child : children) {
      LOG.assertTrue(child.isValid());

      if (!(child instanceof PsiFileSystemItem)) {
        LOG.error("Either PsiFile or PsiDirectory expected as a child of " + child.getParent() + ", but was " + child);
        continue;
      }
      final VirtualFile vFile = ((PsiFileSystemItem) child).getVirtualFile();
      if (vFile == null) {
        continue;
      }
      if (moduleFileIndex != null && !moduleFileIndex.isInContent(vFile)) {
        continue;
      }
      if (filter != null && !filter.shouldShow((PsiFileSystemItem)child)) {
        continue;
      }
      if (child instanceof PsiFile) {
        container.add(new PsiFileNode(child.getProject(), (PsiFile) child, viewSettings));
      }
      else if (child instanceof PsiDirectory) {
        if (withSubDirectories) {
          PsiDirectory dir = (PsiDirectory)child;
          if (!vFile.equals(projectFileIndex.getSourceRootForFile(vFile))) { // if is not a source root
            if (viewSettings.isHideEmptyMiddlePackages() && !skipDirectory(psiDir) && isEmptyMiddleDirectory(dir, true, filter)) {
              processPsiDirectoryChildren(
                dir, directoryChildrenInProject(dir, viewSettings), container, projectFileIndex, moduleFileIndex, viewSettings, true, filter
              ); // expand it recursively
              continue;
            }
          }
          container.add(new PsiDirectoryNode(child.getProject(), (PsiDirectory)child, viewSettings, filter));
        }
      }
    }
  }

  // used only in flatten packages mode
  private void addAllSubpackages(List<? super AbstractTreeNode<?>> container,
                                 PsiDirectory dir,
                                 @Nullable ModuleFileIndex moduleFileIndex,
                                 ViewSettings viewSettings,
                                 @Nullable PsiFileSystemItemFilter filter) {
    final Project project = dir.getProject();
    PsiDirectory[] subdirs = dir.getSubdirectories();
    for (PsiDirectory subdir : subdirs) {
      if (skipDirectory(subdir) || filter != null && !filter.shouldShow(subdir)) {
        continue;
      }
      if (moduleFileIndex != null && !moduleFileIndex.isInContent(subdir.getVirtualFile())) {
        container.add(new PsiDirectoryNode(project, subdir, viewSettings, filter));
        continue;
      }
      if (viewSettings.isHideEmptyMiddlePackages()) {
        if (!isEmptyMiddleDirectory(subdir, false, filter)) {

          container.add(new PsiDirectoryNode(project, subdir, viewSettings, filter));
        }
      }
      else {
        container.add(new PsiDirectoryNode(project, subdir, viewSettings, filter));
      }
      addAllSubpackages(container, subdir, moduleFileIndex, viewSettings, filter);
    }
  }

  public @NotNull Collection<AbstractTreeNode<?>> createFileAndDirectoryNodes(@NotNull List<? extends VirtualFile> files, ViewSettings viewSettings) {
    final List<AbstractTreeNode<?>> children = new ArrayList<>(files.size());
    final PsiManager psiManager = PsiManager.getInstance(myProject);
    for (final VirtualFile virtualFile : files) {
      ContainerUtil.addIfNotNull(children, doCreateNode(virtualFile, psiManager, viewSettings));
    }
    return children;
  }

  protected @Nullable AbstractTreeNode<?> doCreateNode(@NotNull VirtualFile virtualFile,
                                                       @NotNull PsiManager psiManager,
                                                       @Nullable ViewSettings viewSettings) {
    if (virtualFile.isDirectory()) {
      PsiDirectory directory = psiManager.findDirectory(virtualFile);
      if (directory != null) {
        return new PsiDirectoryNode(myProject, directory, viewSettings);
      }
    }
    else {
      PsiFile file = psiManager.findFile(virtualFile);
      if (file != null) {
        return new PsiFileNode(myProject, file, viewSettings);
      }
    }
    return null;
  }
}
