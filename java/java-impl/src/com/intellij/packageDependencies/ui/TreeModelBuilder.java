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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.Icons;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TreeModelBuilder {
  private static final Key<Integer> FILE_COUNT = Key.create("FILE_COUNT");
  private final ProjectFileIndex myFileIndex;
  private final PsiManager myPsiManager;
  private final Project myProject;
  private static final Logger LOG = Logger.getInstance("com.intellij.packageDependencies.ui.TreeModelBuilder");
  private final boolean myShowModuleGroups;

  private static enum ScopeType {
    TEST, SOURCE, LIB
  }

  private final boolean myShowModules;
  private final boolean myGroupByScopeType;
  private final boolean myFlattenPackages;
  private boolean myShowFiles;
  private final boolean myShowIndividualLibs;
  private final Marker myMarker;
  private final boolean myAddUnmarkedFiles;
  private final PackageDependenciesNode myRoot;
  private final Map<ScopeType, Map<Pair<Module, PsiPackage>, PackageNode>> myModulePackageNodes = new HashMap<ScopeType, Map<Pair<Module, PsiPackage>, PackageNode>>();
  private final Map<ScopeType, Map<Pair<OrderEntry, PsiPackage>, PackageNode>> myLibraryPackageNodes = new HashMap<ScopeType, Map<Pair<OrderEntry, PsiPackage>, PackageNode>>();
  private final Map<ScopeType, Map<Module, ModuleNode>> myModuleNodes = new HashMap<ScopeType, Map<Module, ModuleNode>>();
  private final Map<ScopeType, Map<String, ModuleGroupNode>> myModuleGroupNodes = new HashMap<ScopeType, Map<String, ModuleGroupNode>>();
  private final Map<ScopeType, Map<OrderEntry, LibraryNode>> myLibraryNodes = new HashMap<ScopeType, Map<OrderEntry, LibraryNode>>();
  private int myScannedFileCount = 0;
  private int myTotalFileCount = 0;
  private int myMarkedFileCount = 0;
  private GeneralGroupNode myAllLibsNode = null;

  private GeneralGroupNode mySourceRoot = null;
  private GeneralGroupNode myTestRoot = null;
  private GeneralGroupNode myLibsRoot = null;

  private static final Icon LIB_ICON_OPEN = IconLoader.getIcon("/nodes/ppLibOpen.png");
  private static final Icon LIB_ICON_CLOSED = IconLoader.getIcon("/nodes/ppLibClosed.png");
  private static final Icon TEST_ICON = IconLoader.getIcon("/nodes/testSourceFolder.png");
  public static final String PRODUCTION_NAME = AnalysisScopeBundle.message("package.dependencies.production.node.text");
  public static final String TEST_NAME = AnalysisScopeBundle.message("package.dependencies.test.node.text");
  public static final String LIBRARY_NAME = AnalysisScopeBundle.message("package.dependencies.library.node.text");

  public TreeModelBuilder(Project project, boolean showIndividualLibs, Marker marker, DependenciesPanel.DependencyPanelSettings settings) {
    myProject = project;
    myShowModules = settings.UI_SHOW_MODULES;
    myGroupByScopeType = settings.UI_GROUP_BY_SCOPE_TYPE;
    myFlattenPackages = settings.UI_FLATTEN_PACKAGES;
    myShowFiles = settings.UI_SHOW_FILES;
    myShowIndividualLibs = showIndividualLibs;
    myShowModuleGroups = settings.UI_SHOW_MODULE_GROUPS;
    myMarker = marker;
    myAddUnmarkedFiles = !settings.UI_FILTER_LEGALS;
    myRoot = new RootNode();
    myFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    myPsiManager = PsiManager.getInstance(project);

    createMaps(ScopeType.LIB);
    createMaps(ScopeType.SOURCE);
    createMaps(ScopeType.TEST);

    if (myGroupByScopeType) {
      mySourceRoot = new GeneralGroupNode(PRODUCTION_NAME, Icons.PACKAGE_OPEN_ICON, Icons.PACKAGE_ICON);
      myTestRoot = new GeneralGroupNode(TEST_NAME, TEST_ICON, TEST_ICON);
      myLibsRoot = new GeneralGroupNode(LIBRARY_NAME, LIB_ICON_OPEN, LIB_ICON_CLOSED);
      myRoot.add(mySourceRoot);
      myRoot.add(myTestRoot);
      myRoot.add(myLibsRoot);
    }
  }

  private void createMaps(ScopeType scopeType) {
    myModulePackageNodes.put(scopeType, new HashMap<Pair<Module, PsiPackage>, PackageNode>());
    myLibraryPackageNodes.put(scopeType, new HashMap<Pair<OrderEntry, PsiPackage>, PackageNode>());
    myModuleGroupNodes.put(scopeType, new HashMap<String, ModuleGroupNode>());
    myModuleNodes.put(scopeType, new HashMap<Module, ModuleNode>());
    myLibraryNodes.put(scopeType, new HashMap<OrderEntry, LibraryNode>());
  }

  public static synchronized TreeModel createTreeModel(Project project, boolean showProgress, Set<PsiFile> files, Marker marker, DependenciesPanel.DependencyPanelSettings settings) {
    return new TreeModelBuilder(project, true, marker, settings).build(files, showProgress);
  }

  public static synchronized TreeModel createTreeModel(Project project, Marker marker, DependenciesPanel.DependencyPanelSettings settings) {
    return new TreeModelBuilder(project, true, marker, settings).build(project, false);
  }

  public static synchronized TreeModel createTreeModel(Project project,
                                                       boolean showProgress,
                                                       boolean showIndividualLibs,
                                                       Marker marker) {
    return new TreeModelBuilder(project, showIndividualLibs, marker, new DependenciesPanel.DependencyPanelSettings()).build(project, showProgress);
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

      for (VirtualFile root : LibraryUtil.getLibraryRoots(project)) {
        countFilesRecursively(root);
      }

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

        for (VirtualFile root : LibraryUtil.getLibraryRoots(project)) {
          processFilesRecursively(root, psiManager);
        }
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

  private void processFilesRecursively(VirtualFile file, PsiManager psiManager) {
    if (file.isDirectory()) {
      VirtualFile[] children = file.getChildren();
      for (VirtualFile aChildren : children) {
        processFilesRecursively(aChildren, psiManager);
      }
    }
    else {
      final PsiFile psiFile = psiManager.findFile(file);
      if (psiFile != null) { // skip inners & anonymous
        buildFileNode(psiFile);
      }
    }
  }

  private void countFilesRecursively(VirtualFile file) {
    if (file.isDirectory()) {
      VirtualFile[] children = file.getChildren();
      for (VirtualFile aChildren : children) {
        countFilesRecursively(aChildren);
      }
    }
    else {
      counting(file);
    }
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
    LOG.assertTrue(containingDirectory != null);
      PsiPackage aPackage = null;
      if (file instanceof PsiJavaFile){
        aPackage = getFilePackage((PsiJavaFile)file);
      } else {
        final String packageName = myFileIndex.getPackageNameByDirectory(containingDirectory);
        if (packageName != null) {
          aPackage = JavaPsiFacade.getInstance(myPsiManager.getProject()).findPackage(packageName);
        }
      }
      if (aPackage != null) {
        if (myFileIndex.isInLibrarySource(vFile) || myFileIndex.isInLibraryClasses(vFile)) {
          return getLibraryDirNode(aPackage, getLibraryForFile(file));
        }
        else {
          return getModuleDirNode(aPackage, myFileIndex.getModuleForFile(vFile), getFileScopeType(vFile));
        }
      }
      return getModuleNode(myFileIndex.getModuleForFile(vFile), getFileScopeType(vFile));

  }

  @Nullable
  private PsiPackage getFilePackage(PsiJavaFile file) {
    VirtualFile vFile = file.getVirtualFile();
    if (vFile != null && myFileIndex.isInLibrarySource(vFile)) {
      final VirtualFile directory = vFile.getParent();
      if (directory != null) {
        final String packageName = myFileIndex.getPackageNameByDirectory(directory);
        if (packageName != null) {
          return JavaPsiFacade.getInstance(myPsiManager.getProject()).findPackage(packageName);
        }
      }
    }
    return JavaPsiFacade.getInstance(myPsiManager.getProject()).findPackage(file.getPackageName());
  }

  private ScopeType getFileScopeType(VirtualFile file) {
    if (myFileIndex.isLibraryClassFile(file) || myFileIndex.isInLibrarySource(file)) return ScopeType.LIB;
    if (myFileIndex.isInTestSourceContent(file)) return ScopeType.TEST;
    return ScopeType.SOURCE;
  }

  @Nullable
  private OrderEntry getLibraryForFile(PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return null;
    List<OrderEntry> orders = myFileIndex.getOrderEntriesForFile(virtualFile);
    for (OrderEntry order : orders) {
      if (order instanceof LibraryOrderEntry || order instanceof JdkOrderEntry) return order;
    }
    return null;
  }

  private <T> T getMap(Map<ScopeType, T> map, ScopeType scopeType) {
    return map.get(myGroupByScopeType ? scopeType : ScopeType.SOURCE);
  }

  private PackageDependenciesNode getLibraryDirNode(PsiPackage aPackage, OrderEntry libraryOrJdk) {
    if (aPackage == null || aPackage.getName() == null) {
      return getLibraryOrJDKNode(libraryOrJdk);
    }

    if (!myShowModules && !myGroupByScopeType) {
      return getModuleDirNode(aPackage, null, ScopeType.LIB);
    }

    Pair<OrderEntry, PsiPackage> descriptor = new Pair<OrderEntry, PsiPackage>(myShowModules ? libraryOrJdk : null, aPackage);
    PackageNode node = getMap(myLibraryPackageNodes, ScopeType.LIB).get(descriptor);
    if (node != null) return node;

    node = new PackageNode(aPackage, myFlattenPackages);
    getMap(myLibraryPackageNodes, ScopeType.LIB).put(descriptor, node);

    if (myFlattenPackages) {
      getLibraryOrJDKNode(libraryOrJdk).add(node);
    }
    else {
      getLibraryDirNode(aPackage.getParentPackage(), libraryOrJdk).add(node);
    }

    return node;
  }

  private PackageDependenciesNode getModuleDirNode(PsiPackage aPackage, Module module, ScopeType scopeType) {
    if (aPackage == null) {
      return getModuleNode(module, scopeType);
    }

    Pair<Module, PsiPackage> descriptor = new Pair<Module, PsiPackage>(myShowModules ? module : null, aPackage);
    PackageNode node = getMap(myModulePackageNodes, scopeType).get(descriptor);

    if (node != null) return node;

    node = new PackageNode(aPackage, myFlattenPackages);
    getMap(myModulePackageNodes, scopeType).put(descriptor, node);

    if (myFlattenPackages) {
      getModuleNode(module, scopeType).add(node);
    }
    else {
      getModuleDirNode(aPackage.getParentPackage(), module, scopeType).add(node);
    }

    return node;
  }


  @Nullable
  private PackageDependenciesNode getModuleNode(Module module, ScopeType scopeType) {
    if (module == null || !myShowModules) {
      return getRootNode(scopeType);
    }
    ModuleNode node = getMap(myModuleNodes, scopeType).get(module);
    if (node != null) return node;
    node = new ModuleNode(module);
    final ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    final String[] groupPath = moduleManager.getModuleGroupPath(module);
    if (groupPath == null) {
      getMap(myModuleNodes, scopeType).put(module, node);
      getRootNode(scopeType).add(node);
      return node;
    }
    getMap(myModuleNodes, scopeType).put(module, node);
    if (myShowModuleGroups) {
      getParentModuleGroup(groupPath, scopeType).add(node);
    } else {
      getRootNode(scopeType).add(node);
    }
    return node;
  }

  private PackageDependenciesNode getParentModuleGroup(String [] groupPath, ScopeType scopeType){
    ModuleGroupNode groupNode = getMap(myModuleGroupNodes, scopeType).get(groupPath[groupPath.length - 1]);
    if (groupNode == null) {
      groupNode = new ModuleGroupNode(new ModuleGroup(groupPath));
      getMap(myModuleGroupNodes, scopeType).put(groupPath[groupPath.length - 1], groupNode);
      getRootNode(scopeType).add(groupNode);
    }
    if (groupPath.length > 1) {
      String [] path = new String[groupPath.length - 1];
      System.arraycopy(groupPath, 0, path, 0, groupPath.length - 1);
      final PackageDependenciesNode node = getParentModuleGroup(path, scopeType);
      node.add(groupNode);
    }
    return groupNode;
  }

  private PackageDependenciesNode getLibraryOrJDKNode(OrderEntry libraryOrJdk) {
    if (libraryOrJdk == null || !myShowModules) {
      return getRootNode(ScopeType.LIB);
    }

    if (!myShowIndividualLibs) {
      if (myGroupByScopeType) return getRootNode(ScopeType.LIB);
      if (myAllLibsNode == null) {
        myAllLibsNode = new GeneralGroupNode(AnalysisScopeBundle.message("dependencies.libraries.node.text"), LIB_ICON_OPEN, LIB_ICON_CLOSED);
        getRootNode(ScopeType.LIB).add(myAllLibsNode);
      }
      return myAllLibsNode;
    }

    LibraryNode node = getMap(myLibraryNodes, ScopeType.LIB).get(libraryOrJdk);
    if (node != null) return node;
    node = new LibraryNode(libraryOrJdk);
    getMap(myLibraryNodes, ScopeType.LIB).put(libraryOrJdk, node);

    getRootNode(ScopeType.LIB).add(node);
    return node;
  }


  @NotNull
  private PackageDependenciesNode getRootNode(ScopeType scopeType) {
    if (!myGroupByScopeType) {
      return myRoot;
    }
    else {
      if (scopeType == ScopeType.TEST) {
        return myTestRoot;
      }
      else if (scopeType == ScopeType.SOURCE) {
        return mySourceRoot;
      }
      else {
        return myLibsRoot;
      }
    }
  }
}
