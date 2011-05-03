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

package com.intellij.packageDependencies.ui;

import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.scopeView.nodes.BasePsiNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class FileTreeModelBuilder {
  private static final Key<Integer> FILE_COUNT = Key.create("FILE_COUNT");
  private final ProjectFileIndex myFileIndex;
  private final PsiManager myPsiManager;
  private final Project myProject;
  private static final Logger LOG = Logger.getInstance("com.intellij.packageDependencies.ui.TreeModelBuilder");

  private final boolean myShowModuleGroups;
  private final boolean myShowModules;

  private final boolean myFlattenPackages;
  private final boolean myCompactEmptyMiddlePackages;
  private boolean myShowFiles;
  private final Marker myMarker;
  private final boolean myAddUnmarkedFiles;
  private final PackageDependenciesNode myRoot;
  private final Map<PsiDirectory,DirectoryNode> myModuleDirNodes = new HashMap<PsiDirectory, DirectoryNode>();
  private final Map<Module, ModuleNode> myModuleNodes = new HashMap<Module, ModuleNode>();
  private final Map<String, ModuleGroupNode> myModuleGroupNodes = new HashMap<String, ModuleGroupNode>();
  private int myScannedFileCount = 0;
  private int myTotalFileCount = 0;
  private int myMarkedFileCount = 0;

  public FileTreeModelBuilder(Project project, Marker marker, DependenciesPanel.DependencyPanelSettings settings) {
    myProject = project;
    final boolean multiModuleProject = ModuleManager.getInstance(myProject).getModules().length > 1;
    myShowModules = settings.UI_SHOW_MODULES && multiModuleProject;
    myFlattenPackages = settings.UI_FLATTEN_PACKAGES;
    myCompactEmptyMiddlePackages = settings.UI_COMPACT_EMPTY_MIDDLE_PACKAGES;
    myShowFiles = settings.UI_SHOW_FILES;
    myShowModuleGroups = settings.UI_SHOW_MODULE_GROUPS && multiModuleProject;
    myMarker = marker;
    myAddUnmarkedFiles = !settings.UI_FILTER_LEGALS;
    myRoot = new RootNode();
    myFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    myPsiManager = PsiManager.getInstance(project);
  }


  public static synchronized TreeModel createTreeModel(Project project, boolean showProgress, Set<PsiFile> files, Marker marker, DependenciesPanel.DependencyPanelSettings settings) {
    return new FileTreeModelBuilder(project, marker, settings).build(files, showProgress);
  }

  public static synchronized TreeModel createTreeModel(Project project, Marker marker, DependenciesPanel.DependencyPanelSettings settings) {
    return new FileTreeModelBuilder(project, marker, settings).build(project, false);
  }

  public static synchronized TreeModel createTreeModel(Project project, boolean showProgress, Marker marker) {
    return new FileTreeModelBuilder(project, marker, new DependenciesPanel.DependencyPanelSettings()).build(project, showProgress);
  }

  private void countFiles(Project project) {
    final Integer fileCount = project.getUserData(FILE_COUNT);
    if (fileCount == null) {
      myFileIndex.iterateContent(new ContentIterator() {
        public boolean processFile(VirtualFile fileOrDir) {
          if (!fileOrDir.isDirectory()) {
            counting(fileOrDir);
          }
          return true;
        }
      });
      project.putUserData(FILE_COUNT, myTotalFileCount);
    } else {
      myTotalFileCount = fileCount.intValue();
    }
  }

  public static void clearCaches(Project project) {
    project.putUserData(FILE_COUNT, null);
  }

  public TreeModel build(final Project project, boolean showProgress) {
    return build(project, showProgress, false);
  }

  public TreeModel build(final Project project, final boolean showProgress, final boolean sortByType) {
    Runnable buildingRunnable = new Runnable() {
      public void run() {
        countFiles(project);
        final PsiManager psiManager = PsiManager.getInstance(project);
        myFileIndex.iterateContent(new ContentIterator() {
          public boolean processFile(VirtualFile fileOrDir) {
            if (!fileOrDir.isDirectory()) {
              final PsiFile psiFile = psiManager.findFile(fileOrDir);
              if (psiFile != null) {
                buildFileNode(psiFile);
              }
            }
            return true;
          }
        });
      }
    };

    if (showProgress) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(buildingRunnable, AnalysisScopeBundle.message("package.dependencies.build.process.title"), true, project);
    }
    else {
      buildingRunnable.run();
    }

    TreeUtil.sort(myRoot, new DependencyNodeComparator(sortByType));
    return new TreeModel(myRoot, myTotalFileCount, myMarkedFileCount);
  }

  private void counting(final VirtualFile file) {
    myTotalFileCount++;
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.setText(AnalysisScopeBundle.message("package.dependencies.build.progress.text"));
      indicator.setIndeterminate(true);
      indicator.setText2(file.getPresentableUrl());
    }
  }

  private TreeModel build(final Set<PsiFile> files, boolean showProgress) {
    if (files.size() == 1) {
      myShowFiles = true;
    }

    Runnable buildingRunnable = new Runnable() {
      public void run() {
        for (final PsiFile file : files) {
          if (file != null) {
            buildFileNode(file);
          }
        }
      }
    };

    if (showProgress) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(buildingRunnable, AnalysisScopeBundle.message("package.dependencies.build.process.title"), false, myProject);
    }
    else {
      buildingRunnable.run();
    }

    TreeUtil.sort(myRoot, new DependencyNodeComparator());
    return new TreeModel(myRoot, myTotalFileCount, myMarkedFileCount);
  }

  private void buildFileNode(PsiFile file) {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.setIndeterminate(false);
      indicator.setText(AnalysisScopeBundle.message("package.dependencies.build.progress.text"));
      final VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile != null) {
        indicator.setText2(virtualFile.getPresentableUrl());
      }
      indicator.setFraction(((double)myScannedFileCount++) / myTotalFileCount);
    }

    if (file == null || !file.isValid()) return;
    boolean isMarked = myMarker != null && myMarker.isMarked(file);
    if (isMarked) myMarkedFileCount++;
    if (isMarked || myAddUnmarkedFiles) {
      PackageDependenciesNode dirNode = getFileParentNode(file);

      if (myShowFiles) {
        FileNode fileNode = new FileNode(file, isMarked);
        dirNode.add(fileNode);
      }
      else {
        dirNode.addFile(file, isMarked);
      }
    }
  }

  public @NotNull PackageDependenciesNode getFileParentNode(PsiFile file) {
    VirtualFile vFile = file.getVirtualFile();
    LOG.assertTrue(vFile != null);
    final VirtualFile containingDirectory = vFile.getParent();
    LOG.assertTrue(containingDirectory != null, "physical: " + file.isPhysical() + ", " + file.getViewProvider().isPhysical() + ", " + file.getClass().getName());
    return getModuleDirNode(file.getContainingDirectory(), myFileIndex.getModuleForFile(vFile), null);
  }

  @Nullable
  public DefaultMutableTreeNode removeNode(final PsiElement element, PsiDirectory parent) {
    Module module = myFileIndex.getModuleForFile(parent.getVirtualFile());
    if (element instanceof PsiDirectory && myFlattenPackages) {
      final PackageDependenciesNode moduleNode = getModuleNode(module);
      final PsiDirectory psiDirectory = (PsiDirectory)element;
      final VirtualFile virtualFile = psiDirectory.getVirtualFile();
      final PackageDependenciesNode dirNode =
        getModuleDirNode(psiDirectory, myFileIndex.getModuleForFile(virtualFile), null);
      dirNode.removeFromParent();
      return moduleNode;
    }
    DefaultMutableTreeNode dirNode = getModuleDirNode(parent, module, null);
    if (dirNode == null) return null;
    final PackageDependenciesNode[] classOrDirNodes = findNodeForPsiElement((PackageDependenciesNode)dirNode, element);
    if (classOrDirNodes != null){
      for (PackageDependenciesNode classNode : classOrDirNodes) {
        classNode.removeFromParent();
      }
    }

    DefaultMutableTreeNode node = dirNode;
    DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode)node.getParent();
    while (node != null && node.getChildCount() == 0) {
      PsiDirectory directory = parent.getParentDirectory();
      parentNode = (DefaultMutableTreeNode)node.getParent();
      node.removeFromParent();
      if (node instanceof DirectoryNode) {
        while (node != null) {  //clear all compacted links
          myModuleDirNodes.put((PsiDirectory)((DirectoryNode)node).getPsiElement(), null);
          node = ((DirectoryNode)node).getCompactedDirNode();
        }
      } else if (parentNode instanceof ModuleNode) {
        myModuleNodes.put(((ModuleNode)parentNode).getModule(), null);
      } else if (parentNode instanceof ModuleGroupNode) {
        myModuleGroupNodes.put(((ModuleGroupNode)parentNode).getModuleGroupName(), null);
      }
      node = parentNode;
      parent = directory;
    }
    if (myCompactEmptyMiddlePackages && node instanceof DirectoryNode && node.getChildCount() == 1) { //compact
      final TreeNode treeNode = node.getChildAt(0);
      if (treeNode instanceof DirectoryNode){
        node.removeAllChildren();
        for (int i = treeNode.getChildCount() - 1; i >= 0; i--){
          node.add((MutableTreeNode)treeNode.getChildAt(i));
        }
        ((DirectoryNode)node).setCompactedDirNode((DirectoryNode)treeNode);
      }
    }
    return parentNode;
  }

  @Nullable
  public PackageDependenciesNode addFileNode(final PsiFile file){
    boolean isMarked = myMarker != null && myMarker.isMarked(file);
    if (!isMarked) return null;

    final VirtualFile vFile = file.getVirtualFile();
    LOG.assertTrue(vFile != null);
    PsiDirectory dirToReload = file.getContainingDirectory();
    PackageDependenciesNode rootToReload = myModuleDirNodes.get(dirToReload);
    if (rootToReload == null && myFlattenPackages) {
      final Module module = myFileIndex.getModuleForFile(vFile);
      final boolean moduleNodeExist = myModuleNodes.get(module) != null;
      rootToReload = getModuleNode(module);
      if (!moduleNodeExist) {
        rootToReload = null; //need to reload from parent / mostly for problems view
      }
    } else {
      while (rootToReload == null && dirToReload != null){
        dirToReload = dirToReload.getParentDirectory();
        rootToReload = myModuleDirNodes.get(dirToReload);
      }
    }

    PackageDependenciesNode dirNode = getFileParentNode(file);
    dirNode.add(new FileNode(file, isMarked));
    return rootToReload;
  }

  @Nullable
  public PackageDependenciesNode findNode(PsiFile file, final PsiElement psiElement) {
    PackageDependenciesNode parent = getFileParentNode(file);
    PackageDependenciesNode[] nodes = findNodeForPsiElement(parent, file);
    if (nodes == null || nodes.length == 0) {
      return null;
    }
    else {
      for (PackageDependenciesNode node : nodes) {
        if (node.getPsiElement() == psiElement) return node;
      }
      return nodes[0];
    }
  }

  @Nullable
  private static PackageDependenciesNode[] findNodeForPsiElement(PackageDependenciesNode parent, PsiElement element){
    final Set<PackageDependenciesNode> result = new HashSet<PackageDependenciesNode>();
    for (int i = 0; i < parent.getChildCount(); i++){
      final TreeNode treeNode = parent.getChildAt(i);
      if (treeNode instanceof PackageDependenciesNode){
        final PackageDependenciesNode node = (PackageDependenciesNode)treeNode;
        if (element instanceof PsiDirectory && node.getPsiElement() == element){
          return new PackageDependenciesNode[] {node};
        }
        if (element instanceof PsiFile) {
          PsiFile psiFile = null;
          if (node instanceof BasePsiNode) {
            psiFile = ((BasePsiNode)node).getContainingFile();
          }
          else if (node instanceof FileNode) { //non java files
            psiFile = ((PsiFile)node.getPsiElement());
          }
          if (psiFile != null && psiFile.getVirtualFile() == ((PsiFile)element).getVirtualFile()) {
            result.add(node);
          }
        }
      }
    }
    return result.isEmpty() ? null : result.toArray(new PackageDependenciesNode[result.size()]);
  }

  private PackageDependenciesNode getModuleDirNode(PsiDirectory psiDirectory, Module module, DirectoryNode childNode) {
    if (psiDirectory == null) {
      return getModuleNode(module);
    }

    PackageDependenciesNode directoryNode = myModuleDirNodes.get(psiDirectory);
    if (directoryNode != null) {
      if (myCompactEmptyMiddlePackages) {
        DirectoryNode nestedNode = ((DirectoryNode)directoryNode).getCompactedDirNode();
        if (nestedNode != null) { //decompact
          DirectoryNode parentWrapper = nestedNode.getWrapper();
          while (parentWrapper.getWrapper() != null) {
            parentWrapper = parentWrapper.getWrapper();
          }
          for (int i = parentWrapper.getChildCount() - 1; i >= 0; i--) {
            nestedNode.add((MutableTreeNode)parentWrapper.getChildAt(i));
          }
          ((DirectoryNode)directoryNode).setCompactedDirNode(null);
          parentWrapper.add(nestedNode);
          nestedNode.removeUpReference();
          return parentWrapper;
        }
        if (directoryNode.getParent() == null) {    //find first node in tree
          DirectoryNode parentWrapper = ((DirectoryNode)directoryNode).getWrapper();
          if (parentWrapper != null) {
            while (parentWrapper.getWrapper() != null) {
              parentWrapper = parentWrapper.getWrapper();
            }
            return parentWrapper;
          }
        }
      }
      return directoryNode;
    }

    final VirtualFile virtualFile = psiDirectory.getVirtualFile();

    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    final VirtualFile sourceRoot = fileIndex.getSourceRootForFile(virtualFile);
    final VirtualFile contentRoot = fileIndex.getContentRootForFile(virtualFile);

    directoryNode = new DirectoryNode(psiDirectory, myCompactEmptyMiddlePackages, myFlattenPackages);
    myModuleDirNodes.put(psiDirectory, (DirectoryNode)directoryNode);

    final PsiDirectory directory = psiDirectory.getParentDirectory();
    if (!myFlattenPackages && directory != null) {
      if (myCompactEmptyMiddlePackages && sourceRoot != virtualFile && contentRoot != virtualFile) {//compact
        ((DirectoryNode)directoryNode).setCompactedDirNode(childNode);
      }
      final VirtualFile parentDir = directory.getVirtualFile();
      if (fileIndex.getModuleForFile(parentDir) == module) {
        DirectoryNode parentDirectoryNode = myModuleDirNodes.get(directory);
        if (parentDirectoryNode != null
            || !myCompactEmptyMiddlePackages
            || parentDir == sourceRoot || parentDir == contentRoot) {
          getModuleDirNode(directory, module, (DirectoryNode)directoryNode).add(directoryNode);
        }
        else {
          directoryNode = getModuleDirNode(directory, module, (DirectoryNode)directoryNode);
        }
      }
      else {
        getModuleNode(module).add(directoryNode);
      }
    }
    else {
      if (contentRoot == virtualFile) {
        getModuleNode(module).add(directoryNode);
      } else {
        final PsiDirectory root;
        if (sourceRoot != virtualFile && sourceRoot != null) {
          root = myPsiManager.findDirectory(sourceRoot);
        } else if (contentRoot != null) {
          root = myPsiManager.findDirectory(contentRoot);
        } else {
          root = null;
        }
        if (root != null) {
          getModuleDirNode(root, module, null).add(directoryNode);
        }
      }
    }

    return directoryNode;
  }


  @Nullable
  private PackageDependenciesNode getModuleNode(Module module) {
    if (module == null || !myShowModules) {
      return myRoot;
    }
    ModuleNode node = myModuleNodes.get(module);
    if (node != null) return node;
    node = new ModuleNode(module);
    final ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    final String[] groupPath = moduleManager.getModuleGroupPath(module);
    if (groupPath == null) {
      myModuleNodes.put(module, node);
      myRoot.add(node);
      return node;
    }
    myModuleNodes.put(module, node);
    if (myShowModuleGroups) {
      getParentModuleGroup(groupPath).add(node);
    } else {
      myRoot.add(node);
    }
    return node;
  }

  private PackageDependenciesNode getParentModuleGroup(String[] groupPath){
    ModuleGroupNode groupNode = myModuleGroupNodes.get(groupPath[groupPath.length - 1]);
    if (groupNode == null) {
      groupNode = new ModuleGroupNode(new ModuleGroup(groupPath));
      myModuleGroupNodes.put(groupPath[groupPath.length - 1], groupNode);
      myRoot.add(groupNode);
    }
    if (groupPath.length > 1) {
      String [] path = new String[groupPath.length - 1];
      System.arraycopy(groupPath, 0, path, 0, groupPath.length - 1);
      final PackageDependenciesNode node = getParentModuleGroup(path);
      node.add(groupNode);
    }
    return groupNode;
  }


}
