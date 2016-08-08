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

/*
 * User: anna
 * Date: 23-Jan-2008
 */
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.ProjectViewSettings;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.roots.impl.DirectoryInfo;
import com.intellij.openapi.roots.ui.configuration.ModuleSourceRootEditHandler;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.FontUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;

import java.util.*;

public class ProjectViewDirectoryHelper {
  protected static final Logger LOG = Logger.getInstance("#" + ProjectViewDirectoryHelper.class.getName());

  private final Project myProject;
  private final DirectoryIndex myIndex;

  public static ProjectViewDirectoryHelper getInstance(Project project) {
    return ServiceManager.getService(project, ProjectViewDirectoryHelper.class);
  }

  public ProjectViewDirectoryHelper(Project project, DirectoryIndex index) {
    myProject = project;
    myIndex = index;
  }

  public Project getProject() {
    return myProject;
  }


  @Nullable
  public String getLocationString(@NotNull PsiDirectory psiDirectory) {
    boolean includeUrl = ProjectRootsUtil.isModuleContentRoot(psiDirectory);
    return getLocationString(psiDirectory, includeUrl, false);
  }

  @Nullable
  public String getLocationString(@NotNull PsiDirectory psiDirectory, boolean includeUrl, boolean includeRootType) {
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


  @Nullable
  public String getNodeName(ViewSettings settings, Object parentValue, PsiDirectory directory) {
    return directory.getName();
  }

  public boolean skipDirectory(PsiDirectory directory) {
    return true;
  }

  public boolean isEmptyMiddleDirectory(PsiDirectory directory, final boolean strictlyEmpty) {
    return false;
  }

  public boolean supportsFlattenPackages() {
    return false;
  }

  public boolean supportsHideEmptyMiddlePackages() {
    return false;
  }

  public boolean canRepresent(Object element, PsiDirectory directory) {
    if (element instanceof VirtualFile) {
      VirtualFile vFile = (VirtualFile) element;
      return Comparing.equal(directory.getVirtualFile(), vFile);
    }
    return false;
  }

  public Collection<AbstractTreeNode> getDirectoryChildren(final PsiDirectory psiDirectory,
                                                           final ViewSettings settings,
                                                           final boolean withSubDirectories) {
    final List<AbstractTreeNode> children = new ArrayList<>();
    final Project project = psiDirectory.getProject();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final Module module = fileIndex.getModuleForFile(psiDirectory.getVirtualFile());
    final ModuleFileIndex moduleFileIndex = module == null ? null : ModuleRootManager.getInstance(module).getFileIndex();
    if (!settings.isFlattenPackages() || skipDirectory(psiDirectory)) {
      processPsiDirectoryChildren(psiDirectory, directoryChildrenInProject(psiDirectory, settings),
                                  children, fileIndex, null, settings, withSubDirectories);
    }
    else { // source directory in "flatten packages" mode
      final PsiDirectory parentDir = psiDirectory.getParentDirectory();
      if (parentDir == null || skipDirectory(parentDir) && withSubDirectories) {
        addAllSubpackages(children, psiDirectory, moduleFileIndex, settings);
      }
      if (withSubDirectories) {
        PsiDirectory[] subdirs = psiDirectory.getSubdirectories();
        for (PsiDirectory subdir : subdirs) {
          if (!skipDirectory(subdir)) {
            continue;
          }
          VirtualFile directoryFile = subdir.getVirtualFile();

          if (Registry.is("ide.hide.excluded.files")) {
            if (fileIndex.isExcluded(directoryFile)) continue;
          }
          else {
            if (FileTypeRegistry.getInstance().isFileIgnored(directoryFile)) continue;
          }

          children.add(new PsiDirectoryNode(project, subdir, settings));
        }
      }
      processPsiDirectoryChildren(psiDirectory, psiDirectory.getFiles(), children, fileIndex, moduleFileIndex, settings,
                                  withSubDirectories);
    }
    return children;
  }

  public List<VirtualFile> getTopLevelRoots() {
    List<VirtualFile> topLevelContentRoots = new ArrayList<>();
    ProjectRootManager prm = ProjectRootManager.getInstance(myProject);
    ProjectFileIndex index = prm.getFileIndex();

    for (VirtualFile root : prm.getContentRoots()) {
      VirtualFile parent = root.getParent();
      if (!isFileInContent(index, parent)) {
        topLevelContentRoots.add(root);
      }
    }
    return topLevelContentRoots;
  }

  private static boolean isFileInContent(ProjectFileIndex index, VirtualFile file) {
    while (file != null) {
      if (index.isInContent(file)) {
        return true;
      }
      file = file.getParent();
    }
    return false;
  }

  private PsiElement[] directoryChildrenInProject(PsiDirectory psiDirectory, final ViewSettings settings) {
    final VirtualFile dir = psiDirectory.getVirtualFile();
    if (shouldBeShown(dir, settings)) {
      final List<PsiElement> children = new ArrayList<>();
      psiDirectory.processChildren(new PsiElementProcessor<PsiFileSystemItem>() {
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
    Set<PsiElement> directoriesOnTheWayToContentRoots = new THashSet<>();
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

  private boolean shouldBeShown(VirtualFile dir, ViewSettings settings) {
    DirectoryInfo directoryInfo = myIndex.getInfoForFile(dir);
    if (directoryInfo.isInProject()) return true;

    if (!Registry.is("ide.hide.excluded.files") && settings instanceof ProjectViewSettings && ((ProjectViewSettings)settings).isShowExcludedFiles()) {
      return directoryInfo.isExcluded();
    }
    return false;
  }

  // used only for non-flatten packages mode
  public void processPsiDirectoryChildren(final PsiDirectory psiDir,
                                          PsiElement[] children,
                                          List<AbstractTreeNode> container,
                                          ProjectFileIndex projectFileIndex,
                                          @Nullable ModuleFileIndex moduleFileIndex,
                                          ViewSettings viewSettings,
                                          boolean withSubDirectories) {
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
      if (child instanceof PsiFile) {
        container.add(new PsiFileNode(child.getProject(), (PsiFile) child, viewSettings));
      }
      else if (child instanceof PsiDirectory) {
        if (withSubDirectories) {
          PsiDirectory dir = (PsiDirectory)child;
          if (!vFile.equals(projectFileIndex.getSourceRootForFile(vFile))) { // if is not a source root
            if (viewSettings.isHideEmptyMiddlePackages() && !skipDirectory(psiDir) && isEmptyMiddleDirectory(dir, true)) {
              processPsiDirectoryChildren(dir, directoryChildrenInProject(dir, viewSettings),
                                          container, projectFileIndex, moduleFileIndex, viewSettings, withSubDirectories); // expand it recursively
              continue;
            }
          }
          container.add(new PsiDirectoryNode(child.getProject(), (PsiDirectory) child, viewSettings));
        }
      }
    }
  }

  // used only in flatten packages mode
  public void addAllSubpackages(List<AbstractTreeNode> container,
                                PsiDirectory dir,
                                @Nullable ModuleFileIndex moduleFileIndex,
                                ViewSettings viewSettings) {
    final Project project = dir.getProject();
    PsiDirectory[] subdirs = dir.getSubdirectories();
    for (PsiDirectory subdir : subdirs) {
      if (skipDirectory(subdir)) {
        continue;
      }
      if (moduleFileIndex != null && !moduleFileIndex.isInContent(subdir.getVirtualFile())) {
        container.add(new PsiDirectoryNode(project, subdir, viewSettings));
        continue;
      }
      if (viewSettings.isHideEmptyMiddlePackages()) {
        if (!isEmptyMiddleDirectory(subdir, false)) {

          container.add(new PsiDirectoryNode(project, subdir, viewSettings));
        }
      }
      else {
        container.add(new PsiDirectoryNode(project, subdir, viewSettings));
      }
      addAllSubpackages(container, subdir, moduleFileIndex, viewSettings);
    }
  }
}
