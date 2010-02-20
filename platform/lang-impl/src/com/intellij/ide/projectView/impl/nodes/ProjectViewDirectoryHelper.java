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

import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

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
    final VirtualFile directory = psiDirectory.getVirtualFile();
    final VirtualFile contentRootForFile = ProjectRootManager.getInstance(myProject)
      .getFileIndex().getContentRootForFile(directory);
    if (Comparing.equal(contentRootForFile, psiDirectory)) {
      return directory.getPresentableUrl();
    }
    return null;
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

  public boolean showFileInLibClasses(VirtualFile vFile) {
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
      return directory.getVirtualFile() == vFile;
    }
    return false;
  }

  public Collection<AbstractTreeNode> getDirectoryChildren(final PsiDirectory psiDirectory,
                                                           final ViewSettings settings,
                                                           final boolean withSubDirectories) {
    final List<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>();
    final Project project = psiDirectory.getProject();                                                    
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final Module module = fileIndex.getModuleForFile(psiDirectory.getVirtualFile());
    final ModuleFileIndex moduleFileIndex = module == null ? null : ModuleRootManager.getInstance(module).getFileIndex();
    if (!settings.isFlattenPackages() || skipDirectory(psiDirectory)) {
      processPsiDirectoryChildren(psiDirectory, directoryChildrenInProject(psiDirectory),
                                  children, fileIndex, null, settings, withSubDirectories);
    }
    else { // source directory in "flatten packages" mode
      final PsiDirectory parentDir = psiDirectory.getParentDirectory();
      if (parentDir == null || skipDirectory(parentDir) /*|| !rootDirectoryFound(parentDir)*/ && withSubDirectories) {
        addAllSubpackages(children, psiDirectory, moduleFileIndex, settings);
      }
      PsiDirectory[] subdirs = psiDirectory.getSubdirectories();
      for (PsiDirectory subdir : subdirs) {
        if (!skipDirectory(subdir)) {
          continue;
        }
        VirtualFile directoryFile = subdir.getVirtualFile();
        if (fileIndex.isIgnored(directoryFile)) continue;

        if (withSubDirectories) {
          children.add(new PsiDirectoryNode(project, subdir, settings));
        }
      }
      processPsiDirectoryChildren(psiDirectory, psiDirectory.getFiles(), children, fileIndex, moduleFileIndex, settings,
                                  withSubDirectories);
    }
    return children;
  }

  public List<VirtualFile> getTopLevelRoots() {
    List<VirtualFile> topLevelContentRoots = new ArrayList<VirtualFile>();
    ProjectRootManager prm = ProjectRootManager.getInstance(myProject);
    ProjectFileIndex index = prm.getFileIndex();

    for (VirtualFile root : prm.getContentRoots()) {
      VirtualFile parent = root.getParent();
      if (parent == null || !index.isInContent(parent)) {
        topLevelContentRoots.add(root);
      }
    }
    return topLevelContentRoots;
  }

  private PsiElement[] directoryChildrenInProject(PsiDirectory psiDirectory) {
    VirtualFile dir = psiDirectory.getVirtualFile();
    if (myIndex.getInfoForDirectory(dir) != null) {
      return psiDirectory.getChildren();
    }

    PsiManager manager = psiDirectory.getManager();
    Set<PsiElement> directoriesOnTheWayToContentRoots = new THashSet<PsiElement>();
    for (VirtualFile root : getTopLevelRoots()) {
      VirtualFile current = root;
      while (current != null) {
        VirtualFile parent = current.getParent();

        if (parent == dir) {
          final PsiDirectory psi = manager.findDirectory(current);
          if (psi != null) {
            directoriesOnTheWayToContentRoots.add(psi);
          }
        }
        current = parent;
      }
    }

    return directoriesOnTheWayToContentRoots.toArray(new PsiElement[directoriesOnTheWayToContentRoots.size()]);
  }

  // used only for non-flatten packages mode
  public  void processPsiDirectoryChildren(final PsiDirectory psiDir,
                                                  PsiElement[] children,
                                                  List<AbstractTreeNode> container,
                                                  ProjectFileIndex projectFileIndex,
                                                  ModuleFileIndex moduleFileIndex,
                                                  ViewSettings viewSettings,
                                                  boolean withSubDirectories) {
    for (PsiElement child : children) {
      LOG.assertTrue(child.isValid());

      final VirtualFile vFile;
      if (child instanceof PsiFile) {
        vFile = ((PsiFile)child).getVirtualFile();
        addNode(moduleFileIndex, projectFileIndex, psiDir, vFile, container, PsiFileNode.class, child, viewSettings);
      }
      else if (child instanceof PsiDirectory) {
        if (withSubDirectories) {
          PsiDirectory dir = (PsiDirectory)child;
          vFile = dir.getVirtualFile();
          if (!vFile.equals(projectFileIndex.getSourceRootForFile(vFile))) { // if is not a source root
            if (viewSettings.isHideEmptyMiddlePackages() && !skipDirectory(psiDir) && isEmptyMiddleDirectory(dir, true)) {
              processPsiDirectoryChildren(dir, directoryChildrenInProject(dir),
                                          container, projectFileIndex, moduleFileIndex, viewSettings, withSubDirectories); // expand it recursively
              continue;
            }
          }
          addNode(moduleFileIndex, projectFileIndex, psiDir, vFile, container, PsiDirectoryNode.class, child, viewSettings);
        }
      }
      else {
        LOG.error("Either PsiFile or PsiDirectory expected as a child of " + child.getParent() + ", but was " + child);
      }
    }
  }

  public void addNode(ModuleFileIndex moduleFileIndex,
                              ProjectFileIndex projectFileIndex,
                              PsiDirectory psiDir,
                              VirtualFile vFile,
                              List<AbstractTreeNode> container,
                              Class<? extends AbstractTreeNode> nodeClass,
                              PsiElement element,
                              final ViewSettings settings) {
    if (vFile == null) {
      return;
    }
    // this check makes sense for classes not in library content only
    if (moduleFileIndex != null && !moduleFileIndex.isInContent(vFile)) {
      return;
    }
    /*
    final boolean childInLibraryClasses = projectFileIndex.isInLibraryClasses(vFile);
    if (!projectFileIndex.isInSourceContent(vFile)) {
      if (childInLibraryClasses) {
        final VirtualFile psiDirVFile = psiDir.getVirtualFile();
        final boolean parentInLibraryContent =
          projectFileIndex.isInLibraryClasses(psiDirVFile) || projectFileIndex.isInLibrarySource(psiDirVFile);
        if (!parentInLibraryContent) {
          return;
        }
      }
    }
    if (childInLibraryClasses && !projectFileIndex.isInContent(vFile) && !showFileInLibClasses(vFile)) {
      return; // skip java sources in classpath
    }
    */

    try {
      container.add(ProjectViewNode.createTreeNode(nodeClass, element.getProject(), element, settings));
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  // used only in flatten packages mode
  public void addAllSubpackages(List<AbstractTreeNode> container,
                                        PsiDirectory dir,
                                        ModuleFileIndex moduleFileIndex,
                                        ViewSettings viewSettings) {
    final Project project = dir.getProject();
    PsiDirectory[] subdirs = dir.getSubdirectories();
    for (PsiDirectory subdir : subdirs) {
      if (skipDirectory(subdir)) {
        continue;
      }
      if (moduleFileIndex != null) {
        if (!moduleFileIndex.isInContent(subdir.getVirtualFile())) {
          container.add(new PsiDirectoryNode(project, subdir, viewSettings));
          continue;
        }
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
