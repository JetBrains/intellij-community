// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packageDependencies.ui;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleGrouper;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TreeModelBuilder {
  private static final Logger LOG = Logger.getInstance(TreeModelBuilder.class);

  private final ProjectFileIndex myFileIndex;
  private final Project myProject;
  private final boolean myShowModuleGroups;
  protected final JavaPsiFacade myJavaPsiFacade;
  private static final Key<Integer> FILE_COUNT = Key.create("packages.FILE_COUNT");

  private enum ScopeType {
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
  private final Map<ScopeType, Map<Pair<Module, PsiPackage>, PackageNode>> myModulePackageNodes = new HashMap<>();
  private final Map<ScopeType, Map<Pair<OrderEntry, PsiPackage>, PackageNode>> myLibraryPackageNodes = new HashMap<>();
  private final Map<ScopeType, Map<Module, ModuleNode>> myModuleNodes = new HashMap<>();
  private final Map<ScopeType, Map<String, ModuleGroupNode>> myModuleGroupNodes = new HashMap<>();
  private final Map<ScopeType, Map<OrderEntry, LibraryNode>> myLibraryNodes = new HashMap<>();
  private final ModuleGrouper myModuleGrouper;
  private int myScannedFileCount;
  private int myTotalFileCount;
  private int myMarkedFileCount;
  private GeneralGroupNode myAllLibsNode;

  private GeneralGroupNode mySourceRoot;
  private GeneralGroupNode myTestRoot;
  private GeneralGroupNode myLibsRoot;

  public TreeModelBuilder(@NotNull Project project, boolean showIndividualLibs, Marker marker, DependenciesPanel.DependencyPanelSettings settings) {
    myProject = project;
    final boolean multiModuleProject = ModuleManager.getInstance(project).getModules().length > 1;
    myShowModules = settings.UI_SHOW_MODULES && multiModuleProject;
    myGroupByScopeType = settings.UI_GROUP_BY_SCOPE_TYPE;
    myFlattenPackages = settings.UI_FLATTEN_PACKAGES;
    myShowFiles = settings.UI_SHOW_FILES;
    myShowIndividualLibs = showIndividualLibs;
    myShowModuleGroups = settings.UI_SHOW_MODULE_GROUPS && multiModuleProject;
    myModuleGrouper = ModuleGrouper.instanceFor(project);
    myMarker = marker;
    myAddUnmarkedFiles = !settings.UI_FILTER_LEGALS;
    myRoot = new RootNode(project);
    myFileIndex = ProjectRootManager.getInstance(project).getFileIndex();

    createMaps(ScopeType.LIB);
    createMaps(ScopeType.SOURCE);
    createMaps(ScopeType.TEST);

    if (myGroupByScopeType) {
      mySourceRoot = new GeneralGroupNode(getProductionName(), IconManager.getInstance().getPlatformIcon(PlatformIcons.Package), project);
      myTestRoot = new GeneralGroupNode(getTestName(), AllIcons.Nodes.TestSourceFolder, project);
      myLibsRoot = new GeneralGroupNode(getLibraryName(), AllIcons.Nodes.PpLibFolder, project);
      myRoot.add(mySourceRoot);
      myRoot.add(myTestRoot);
      myRoot.add(myLibsRoot);
    }
    myJavaPsiFacade = JavaPsiFacade.getInstance(myProject);
  }

  private void createMaps(ScopeType scopeType) {
    myModulePackageNodes.put(scopeType, new HashMap<>());
    myLibraryPackageNodes.put(scopeType, new HashMap<>());
    myModuleGroupNodes.put(scopeType, new HashMap<>());
    myModuleNodes.put(scopeType, new HashMap<>());
    myLibraryNodes.put(scopeType, new HashMap<>());
  }

  public static synchronized TreeModel createTreeModel(Project project, boolean showProgress, Set<? extends PsiFile> files, Marker marker, DependenciesPanel.DependencyPanelSettings settings) {
    return new TreeModelBuilder(project, true, marker, settings).build(files, showProgress);
  }

  public static synchronized TreeModel createTreeModel(Project project, Marker marker, DependenciesPanel.DependencyPanelSettings settings) {
    return new TreeModelBuilder(project, true, marker, settings).build(project);
  }

  public static synchronized TreeModel createTreeModel(Project project,
                                                       boolean showIndividualLibs,
                                                       Marker marker) {
    return new TreeModelBuilder(project, showIndividualLibs, marker, new DependenciesPanel.DependencyPanelSettings()).build(project);
  }

  private void countFiles(Project project) {
    final Integer fileCount = project.getUserData(FILE_COUNT);
    if (fileCount == null) {
      myFileIndex.iterateContent(fileOrDir -> {
        if (!fileOrDir.isDirectory()) {
          counting();
        }
        return true;
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

  public TreeModel build(final Project project) {
    Runnable buildingRunnable = () -> {
      countFiles(project);
      myFileIndex.iterateContent(new ContentIterator() {
        PackageDependenciesNode lastParent;
        VirtualFile dir;
        @Override
        public boolean processFile(@NotNull VirtualFile fileOrDir) {
          if (!fileOrDir.isDirectory()) {
            if (lastParent != null && !Comparing.equal(dir, fileOrDir.getParent())) {
              lastParent = null;
            }
            lastParent = buildFileNode(fileOrDir, lastParent);
            dir = fileOrDir.getParent();
          } else {
            lastParent = null;
          }
          return true;
        }
      });

      for (VirtualFile root : LibraryUtil.getLibraryRoots(project)) {
        processFilesRecursively(root);
      }
    };

    buildingRunnable.run();

    return new TreeModel(myRoot, myTotalFileCount, myMarkedFileCount);
  }

  private void processFilesRecursively(@NotNull VirtualFile file) {
    VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor<Void>() {
      private PackageDependenciesNode parent;

      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        if (file.isDirectory()) {
          parent = null;
        }
        else {
          parent = buildFileNode(file, parent);
        }
        return true;
      }

      @Override
      public void afterChildrenVisited(@NotNull VirtualFile file) {
        if (file.isDirectory()) {
          parent = null;
        }
      }
    });
  }

  private void countFilesRecursively(VirtualFile file) {
    VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor<Void>() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        if (!file.isDirectory()) {
          counting();
        }
        return true;
      }
    });
  }

  private void counting() {
    myTotalFileCount++;
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.setText(getScanningPackagesMessage());
      indicator.setIndeterminate(true);
    }
  }

  private TreeModel build(final Set<? extends PsiFile> files, boolean showProgress) {
    if (files.size() == 1) {
      myShowFiles = true;
    }

    Runnable buildingRunnable = () -> {
      for (final PsiFile file : files) {
        if (file != null) {
          VirtualFile virtualFile = file.getVirtualFile();
          if (virtualFile != null) {
            buildFileNode(virtualFile, null);
          }
        }
      }
    };

    if (showProgress) {
      final String title = CodeInsightBundle.message("package.dependencies.build.process.title");
      ProgressManager.getInstance().runProcessWithProgressSynchronously(buildingRunnable, title, false, myProject);
    }
    else {
      buildingRunnable.run();
    }

    TreeUtil.sortRecursively(myRoot, new DependencyNodeComparator());
    return new TreeModel(myRoot, myTotalFileCount, myMarkedFileCount);
  }

  private @Nullable PackageDependenciesNode buildFileNode(@NotNull VirtualFile file, @Nullable PackageDependenciesNode parent) {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.setText(getScanningPackagesMessage());
      indicator.setFraction(((double)myScannedFileCount++) / myTotalFileCount);
    }

    boolean isMarked = myMarker != null && myMarker.isMarked(file);
    if (isMarked) myMarkedFileCount++;
    if (isMarked || myAddUnmarkedFiles) {
      PackageDependenciesNode dirNode = parent != null ? parent : getFileParentNode(file);
      if (dirNode == null) return null;

      if (myShowFiles) {
        FileNode fileNode = new FileNode(file, myProject, isMarked);
        dirNode.add(fileNode);
      }
      else {
        dirNode.addFile(file, isMarked);
      }
      return dirNode;
    }
    return null;
  }

  public @Nullable PackageDependenciesNode getFileParentNode(VirtualFile vFile) {
    LOG.assertTrue(vFile != null);
    PsiPackage aPackage = null;
    final String packageName = PackageIndex.getInstance(myProject).getPackageName(vFile);
    if (packageName != null) {
      aPackage = myJavaPsiFacade.findPackage(packageName);
    }
    if (aPackage != null) {
        if (myFileIndex.isInLibrary(vFile)) {
          return getLibraryDirNode(aPackage, getLibraryForFile(vFile));
        }
        else {
          return getModuleDirNode(aPackage, myFileIndex.getModuleForFile(vFile), getFileScopeType(vFile));
        }
      }
      return myFileIndex.isInLibrarySource(vFile) ? null : getModuleNode(myFileIndex.getModuleForFile(vFile), getFileScopeType(vFile));

  }

  private ScopeType getFileScopeType(VirtualFile file) {
    if (myFileIndex.isInLibraryClasses(file) || myFileIndex.isInLibrarySource(file)) return ScopeType.LIB;
    if (myFileIndex.isInTestSourceContent(file)) return ScopeType.TEST;
    return ScopeType.SOURCE;
  }

  private @Nullable OrderEntry getLibraryForFile(VirtualFile virtualFile) {
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

    Pair<OrderEntry, PsiPackage> descriptor = Pair.create(myShowIndividualLibs ? libraryOrJdk : null, aPackage);
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

    Pair<Module, PsiPackage> descriptor = Pair.create(myShowModules ? module : null, aPackage);
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


  private @NotNull PackageDependenciesNode getModuleNode(Module module, ScopeType scopeType) {
    if (module == null || !myShowModules) {
      return getRootNode(scopeType);
    }
    ModuleNode node = getMap(myModuleNodes, scopeType).get(module);
    if (node != null) return node;
    node = new ModuleNode(module, myShowModuleGroups ? myModuleGrouper : null);
    final List<String> groupPath = myModuleGrouper.getGroupPath(module);
    if (groupPath.isEmpty()) {
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

  private PackageDependenciesNode getParentModuleGroup(List<String> groupPath, ScopeType scopeType){
    final String key = StringUtil.join(groupPath, ",");
    ModuleGroupNode groupNode = getMap(myModuleGroupNodes, scopeType).get(key);
    if (groupNode == null) {
      groupNode = new ModuleGroupNode(new ModuleGroup(groupPath), myProject);
      getMap(myModuleGroupNodes, scopeType).put(key, groupNode);
      getRootNode(scopeType).add(groupNode);
    }
    if (groupPath.size() > 1) {
      final PackageDependenciesNode node = getParentModuleGroup(groupPath.subList(0, groupPath.size()-1), scopeType);
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
        myAllLibsNode = new GeneralGroupNode(JavaBundle.message("dependencies.libraries.node.text"),
                                             AllIcons.Nodes.PpLibFolder,
                                             myProject);
        getRootNode(ScopeType.LIB).add(myAllLibsNode);
      }
      return myAllLibsNode;
    }

    LibraryNode node = getMap(myLibraryNodes, ScopeType.LIB).get(libraryOrJdk);
    if (node != null) return node;
    node = new LibraryNode(libraryOrJdk, myProject);
    getMap(myLibraryNodes, ScopeType.LIB).put(libraryOrJdk, node);

    getRootNode(ScopeType.LIB).add(node);
    return node;
  }


  private @NotNull PackageDependenciesNode getRootNode(ScopeType scopeType) {
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

  public static @NlsContexts.ProgressText String getScanningPackagesMessage() {
    return CodeInsightBundle.message("package.dependencies.build.progress.text");
  }

  public static String getProductionName() {
    return JavaBundle.message("package.dependencies.production.node.text");
  }

  public static String getTestName() {
    return JavaBundle.message("package.dependencies.test.node.text");
  }

  public static String getLibraryName() {
    return CodeInsightBundle.message("package.dependencies.library.node.text");
  }
}
