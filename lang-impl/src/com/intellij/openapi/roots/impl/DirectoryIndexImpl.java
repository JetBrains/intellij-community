package com.intellij.openapi.roots.impl;

import com.intellij.AppTopics;
import com.intellij.ProjectTopics;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.psi.impl.PsiManagerConfiguration;
import com.intellij.util.*;
import com.intellij.util.containers.HashMap;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.THashMap;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

public class DirectoryIndexImpl extends DirectoryIndex implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.DirectoryIndexImpl");

  private final Project myProject;

  private boolean myInitialized = false;
  private boolean myDisposed = false;

  private final boolean myIsLasyMode;

  private Map<VirtualFile, Set<String>> myExcludeRootsMap;
  private Map<VirtualFile, DirectoryInfo> myDirToInfoMap = Collections.synchronizedMap(new THashMap<VirtualFile, DirectoryInfo>());
  private Map<String, VirtualFile[]> myPackageNameToDirsMap = Collections.synchronizedMap(new THashMap<String, VirtualFile[]>());

  private DirectoryIndexExcludePolicy[] myExcludePolicies;
  private VirtualFileListener myVirtualFileListener;
  private MessageBusConnection myConnection;

  public DirectoryIndexImpl(Project project, PsiManagerConfiguration psiManagerConfiguration, StartupManager startupManager) {
    myProject = project;
    myConnection = project.getMessageBus().connect();

    myIsLasyMode = !psiManagerConfiguration.REPOSITORY_ENABLED;
    ((StartupManagerEx)startupManager).registerPreStartupActivity(new Runnable() {
      public void run() {
        initialize();
      }
    });
    myExcludePolicies = Extensions.getExtensions(DirectoryIndexExcludePolicy.EP_NAME, myProject);
  }

  @NotNull
  public String getComponentName() {
    return "DirectoryIndex";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    if (myInitialized) {
      myConnection.disconnect();
      VirtualFileManager.getInstance().removeVirtualFileListener(myVirtualFileListener);
    }
    myDisposed = true;
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @TestOnly
  public void checkConsistency() {
    doCheckConsistency(false);
    doCheckConsistency(true);
  }

  @TestOnly
  private void doCheckConsistency(boolean reverseAllSets) {
    Assert.assertTrue(myInitialized);
    Assert.assertTrue(!myDisposed);

    Map<VirtualFile, DirectoryInfo> oldDirToInfoMap = myDirToInfoMap;
    myDirToInfoMap = new THashMap<VirtualFile, DirectoryInfo>();

    Map<String, VirtualFile[]> oldPackageNameToDirsMap = myPackageNameToDirsMap;
    myPackageNameToDirsMap = new THashMap<String, VirtualFile[]>();

    doInitialize(reverseAllSets, null);

    if (myIsLasyMode) {
      Map<VirtualFile, DirectoryInfo> newDirToInfoMap = myDirToInfoMap;
      Map<String, VirtualFile[]> newPackageNameToDirsMap = myPackageNameToDirsMap;
      myDirToInfoMap = oldDirToInfoMap;
      myPackageNameToDirsMap = oldPackageNameToDirsMap;

      Set<VirtualFile> allDirsSet = newDirToInfoMap.keySet();
      for (VirtualFile dir : allDirsSet) {
        getInfoForDirectory(dir);
      }

      myDirToInfoMap = newDirToInfoMap;
      myPackageNameToDirsMap = newPackageNameToDirsMap;
    }

    Set<VirtualFile> keySet = myDirToInfoMap.keySet();
    Assert.assertEquals(keySet.size(), oldDirToInfoMap.keySet().size());
    for (VirtualFile file : keySet) {
      DirectoryInfo info1 = myDirToInfoMap.get(file);
      DirectoryInfo info2 = oldDirToInfoMap.get(file);
      Assert.assertEquals(info1, info2);
    }

    Assert.assertEquals(myPackageNameToDirsMap.keySet().size(), oldPackageNameToDirsMap.keySet().size());
    for (Map.Entry<String, VirtualFile[]> entry : myPackageNameToDirsMap.entrySet()) {
      String packageName = entry.getKey();
      VirtualFile[] dirs = entry.getValue();
      VirtualFile[] dirs1 = oldPackageNameToDirsMap.get(packageName);

      HashSet<VirtualFile> set1 = new HashSet<VirtualFile>();
      set1.addAll(Arrays.asList(dirs));
      HashSet<VirtualFile> set2 = new HashSet<VirtualFile>();
      set2.addAll(Arrays.asList(dirs1));
      Assert.assertEquals(set1, set2);
    }
  }

  public void initialize() {
    if (myInitialized) {
      LOG.error("Directory index is already initialized.");
      return;
    }

    if (myDisposed) {
      LOG.error("Directory index is aleady disposed for this project");
      return;
    }

    myInitialized = true;

    subscribeToFileChanges();
    doInitialize();
  }

  private void subscribeToFileChanges() {
    myVirtualFileListener = new MyVirtualFileListener();
    VirtualFileManager.getInstance().addVirtualFileListener(myVirtualFileListener);

    myConnection.subscribe(AppTopics.FILE_TYPES, new FileTypeListener() {
      public void beforeFileTypesChanged(FileTypeEvent event) {
      }

      public void fileTypesChanged(FileTypeEvent event) {
        doInitialize();
      }
    });

    myConnection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      public void beforeRootsChange(ModuleRootEvent event) {
      }

      public void rootsChanged(ModuleRootEvent event) {
        doInitialize();
      }
    });
  }

  private void doInitialize() {
    if (myIsLasyMode) {
      cleapAllMaps();
    }
    else {
      doInitialize(false, null);
    }
  }

  private void doInitialize(boolean reverseAllSets/* for testing order independence*/, VirtualFile forDir/* in LAZY_MODE only*/) {
    ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if (progress == null) progress = new EmptyProgressIndicator();

    progress.pushState();

    progress.checkCanceled();
    progress.setText(ProjectBundle.message("project.index.scanning.files.progress"));

    if (forDir == null) cleapAllMaps();
    else createMapsFor(forDir);

    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    if (reverseAllSets) modules = ArrayUtil.reverseArray(modules);

    initExcludedDirMap(modules, progress);

    for (Module module : modules) {
      initModuleContents(module, forDir, reverseAllSets, progress);
      initModuleSources(module, forDir, reverseAllSets, progress);
      initLibrarySources(module, forDir, progress);
      initLibraryClasses(module, forDir, progress);
    }

    progress.checkCanceled();
    progress.setText2("");

    for (Module module : modules) {
      initOrderEntries(module, forDir);
    }

    progress.popState();
  }

  private void cleapAllMaps() {
    myDirToInfoMap.clear();
    myPackageNameToDirsMap.clear();
  }

  private void createMapsFor(VirtualFile forDir) {
    // clear map for all ancestors to not interfer with previous results
    VirtualFile dir = forDir;
    do {
      myDirToInfoMap.remove(dir);
      dir = dir.getParent();
    }
    while (dir != null);
  }

  private void initExcludedDirMap(Module[] modules, ProgressIndicator progress) {
    progress.checkCanceled();
    progress.setText2(ProjectBundle.message("project.index.building.exclude.roots.progress"));

    // exclude roots should be merged to prevent including excluded dirs of an inner module into the outer
    // exclude root should exclude from its content root and all outer content roots
    Map<VirtualFile, Set<String>> result = new THashMap<VirtualFile, Set<String>>();

    for (Module module : modules) {
      for (ContentEntry contentEntry : getContentEntries(module)) {
        VirtualFile contentRoot = contentEntry.getFile();
        if (contentRoot == null) continue;

        ExcludeFolder[] excludeRoots = contentEntry.getExcludeFolders();
        for (ExcludeFolder excludeRoot : excludeRoots) {
          // Output paths should be excluded (if marked as such) regardless if they're under corresponding module's content root
          if (excludeRoot.getFile() != null) {
            if (!contentRoot.getUrl().startsWith(excludeRoot.getUrl())) {
              if (isExcludeRootForModule(module, excludeRoot.getFile())) {
                putForFileAndAllAncestors(result, excludeRoot.getFile(), excludeRoot.getUrl());
              }
            }
          }

          putForFileAndAllAncestors(result, contentRoot, excludeRoot.getUrl());
        }
      }
    }

    for(DirectoryIndexExcludePolicy policy: myExcludePolicies) {
      for(VirtualFile file: policy.getExcludeRootsForProject()) {
        putForFileAndAllAncestors(result, file, file.getUrl());
      }
    }

    myExcludeRootsMap = result;
  }

  private static void putForFileAndAllAncestors(Map<VirtualFile, Set<String>> map, VirtualFile file, String value) {
    while (true) {
      Set<String> set = map.get(file);
      if (set == null) {
        set = new HashSet<String>();
        map.put(file, set);
      }
      set.add(value);

      file = file.getParent();
      if (file == null) break;
    }
  }

  private boolean isExcludeRootForModule(Module module, VirtualFile excludeRoot) {
    for(DirectoryIndexExcludePolicy policy: myExcludePolicies) {
      if (policy.isExcludeRootForModule(module, excludeRoot)) return true;
    }
    return false;
  }

  private ContentEntry[] getContentEntries(Module module) {
    return ModuleRootManager.getInstance(module).getContentEntries();
  }

  private OrderEntry[] getOrderEntries(Module module) {
    return ModuleRootManager.getInstance(module).getOrderEntries();
  }

  private void initModuleContents(Module module,
                                  VirtualFile forDir,
                                  boolean reverseAllSets,
                                  ProgressIndicator progress) {
    progress.checkCanceled();
    progress.setText2(ProjectBundle.message("project.index.processing.module.content.progress", module.getName()));

    ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    VirtualFile[] contentRoots = rootManager.getContentRoots();
    if (reverseAllSets) {
      contentRoots = ArrayUtil.reverseArray(contentRoots);
    }

    for (final VirtualFile contentRoot : contentRoots) {
      fillMapWithModuleContent(contentRoot, module, contentRoot, forDir);
    }
  }

  private void fillMapWithModuleContent(VirtualFile dir,
                                        Module module,
                                        VirtualFile contentRoot,
                                        VirtualFile forDir) {
    if (isExcluded(contentRoot, dir)) return;
    if (isIgnored(dir)) return;

    if (forDir != null) {
      if (!VfsUtil.isAncestor(dir, forDir, false)) return;
    }

    DirectoryInfo info = getOrCreateDirInfo(dir);

    if (info.module != null) { // module contents overlap
      DirectoryInfo parentInfo = myDirToInfoMap.get(dir.getParent());
      if (parentInfo == null || !info.module.equals(parentInfo.module)) return; // content of another module is below this module's content
    }

    VirtualFile[] children = dir.getChildren();
    for (VirtualFile child : children) {
      if (child.isDirectory()) {
        fillMapWithModuleContent(child, module, contentRoot, forDir);
      }
    }

    // important to change module AFTER processing children - to handle overlapping modules
    info.module = module;
    info.contentRoot = contentRoot;
  }

  private boolean isExcluded(VirtualFile root, VirtualFile dir) {
    Set<String> excludes = myExcludeRootsMap.get(root);
    return excludes != null && excludes.contains(dir.getUrl());
  }

  private void initModuleSources(Module module,
                                 VirtualFile forDir,
                                 boolean reverseAllSets,
                                 ProgressIndicator progress) {
    progress.checkCanceled();
    progress.setText2(ProjectBundle.message("project.index.processing.module.sources.progress", module.getName()));

    ContentEntry[] contentEntries = getContentEntries(module);
    
    if (reverseAllSets) {
      contentEntries = ArrayUtil.reverseArray(contentEntries);
    }

    for (ContentEntry contentEntry : contentEntries) {
      SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
      if (reverseAllSets) {
        sourceFolders = ArrayUtil.reverseArray(sourceFolders);
      }
      for (SourceFolder sourceFolder : sourceFolders) {
        VirtualFile dir = sourceFolder.getFile();
        if (dir != null) {
          fillMapWithModuleSource(dir, module, sourceFolder.getPackagePrefix(), dir, sourceFolder.isTestSource(), forDir);
        }
      }
    }
  }

  private void fillMapWithModuleSource(VirtualFile dir,
                                       Module module,
                                       String packageName,
                                       VirtualFile sourceRoot,
                                       boolean isTestSource,
                                       VirtualFile forDir) {
    DirectoryInfo info = myDirToInfoMap.get(dir);
    if (info == null) return;
    if (!module.equals(info.module)) return;

    if (forDir != null) {
      if (!VfsUtil.isAncestor(dir, forDir, false)) return;
    }

    if (info.isInModuleSource) { // module sources overlap
      if (info.packageName != null && info.packageName.length() == 0) return; // another source root starts here
    }

    info.isInModuleSource = true;
    info.isTestSource = isTestSource;
    info.sourceRoot = sourceRoot;
    setPackageName(dir, info, packageName);

    VirtualFile[] children = dir.getChildren();
    for (VirtualFile child : children) {
      if (child.isDirectory()) {
        String childPackageName = getPackageNameForSubdir(packageName, child.getName());
        fillMapWithModuleSource(child, module, childPackageName, sourceRoot, isTestSource, forDir);
      }
    }
  }

  private void initLibrarySources(Module module, VirtualFile forDir, ProgressIndicator progress) {
    progress.checkCanceled();
    progress.setText2(ProjectBundle.message("project.index.processing.library.sources.progress", module.getName()));

    for (OrderEntry orderEntry : getOrderEntries(module)) {
      boolean isLibrary = orderEntry instanceof LibraryOrderEntry || orderEntry instanceof JdkOrderEntry;
      if (isLibrary) {
        VirtualFile[] sourceRoots = orderEntry.getFiles(OrderRootType.SOURCES);
        for (final VirtualFile sourceRoot : sourceRoots) {
          fillMapWithLibrarySources(sourceRoot, "", sourceRoot, forDir);
        }
      }
    }
  }

  private void fillMapWithLibrarySources(VirtualFile dir,
                                         String packageName,
                                         VirtualFile sourceRoot,
                                         VirtualFile forDir) {
    if (isIgnored(dir)) return;

    if (forDir != null) {
      if (!VfsUtil.isAncestor(dir, forDir, false)) return;
    }

    DirectoryInfo info = getOrCreateDirInfo(dir);

    if (info.isInLibrarySource) { // library sources overlap
      if (info.packageName != null && info.packageName.length() == 0) return; // another library source root starts here
    }

    info.isInModuleSource = false;
    info.isInLibrarySource = true;
    info.sourceRoot = sourceRoot;
    setPackageName(dir, info, packageName);

    VirtualFile[] children = dir.getChildren();
    for (VirtualFile child : children) {
      if (child.isDirectory()) {
        String childPackageName = getPackageNameForSubdir(packageName, child.getName());
        fillMapWithLibrarySources(child, childPackageName, sourceRoot, forDir);
      }
    }
  }

  private void initLibraryClasses(Module module, VirtualFile forDir, ProgressIndicator progress) {
    progress.checkCanceled();
    progress.setText2(ProjectBundle.message("project.index.processing.library.classes.progress", module.getName()));

    for (OrderEntry orderEntry : getOrderEntries(module)) {
      boolean isLibrary = orderEntry instanceof LibraryOrderEntry || orderEntry instanceof JdkOrderEntry;
      if (isLibrary) {
        VirtualFile[] classRoots = orderEntry.getFiles(OrderRootType.CLASSES);
        for (final VirtualFile classRoot : classRoots) {
          fillMapWithLibraryClasses(classRoot, "", classRoot, forDir);
        }
      }
    }
  }

  private void fillMapWithLibraryClasses(VirtualFile dir, String packageName, VirtualFile classRoot, VirtualFile forDir) {
    if (isIgnored(dir)) return;

    if (forDir != null) {
      if (!VfsUtil.isAncestor(dir, forDir, false)) return;
    }

    DirectoryInfo info = getOrCreateDirInfo(dir);

    if (info.libraryClassRoot != null) { // library classes overlap
      if (info.packageName != null && info.packageName.length() == 0) return; // another library root starts here
    }

    info.libraryClassRoot = classRoot;

    if (!info.isInModuleSource && !info.isInLibrarySource) {
      setPackageName(dir, info, packageName);
    }

    VirtualFile[] children = dir.getChildren();
    for (VirtualFile child : children) {
      if (child.isDirectory()) {
        String childPackageName = getPackageNameForSubdir(packageName, child.getName());
        fillMapWithLibraryClasses(child, childPackageName, classRoot, forDir);
      }
    }
  }

  private void initOrderEntries(Module module, VirtualFile forDir) {
    Map<VirtualFile, List<OrderEntry>> depEntries = new HashMap<VirtualFile, List<OrderEntry>>();
    Map<VirtualFile, List<OrderEntry>> libClassRootEntries = new HashMap<VirtualFile, List<OrderEntry>>();
    Map<VirtualFile, List<OrderEntry>> libSourceRootEntries = new HashMap<VirtualFile, List<OrderEntry>>();

    for (OrderEntry orderEntry : getOrderEntries(module)) {
      if (orderEntry instanceof ModuleOrderEntry) {
        VirtualFile[] importedClassRoots = orderEntry.getFiles(OrderRootType.COMPILATION_CLASSES);
        for (VirtualFile importedClassRoot : importedClassRoots) {
          addEntryToMap(importedClassRoot, orderEntry, depEntries);
        }
        VirtualFile[] sourceRoots = orderEntry.getFiles(OrderRootType.SOURCES);
        for (VirtualFile sourceRoot : sourceRoots) {
          addEntryToMap(sourceRoot, orderEntry, depEntries);
        }
      }
      else if (orderEntry instanceof ModuleSourceOrderEntry) {
        List<OrderEntry> oneEntryList = Arrays.asList(new OrderEntry[]{orderEntry});
        Module entryModule = orderEntry.getOwnerModule();

        VirtualFile[] sourceRoots = orderEntry.getFiles(OrderRootType.SOURCES);
        for (VirtualFile sourceRoot : sourceRoots) {
          fillMapWithOrderEntries(sourceRoot, oneEntryList, entryModule, null, null, forDir, null, null);
        }
      }
      else if (orderEntry instanceof LibraryOrderEntry || orderEntry instanceof JdkOrderEntry) {
        VirtualFile[] classRoots = orderEntry.getFiles(OrderRootType.CLASSES);
        for (VirtualFile classRoot : classRoots) {
          addEntryToMap(classRoot, orderEntry, libClassRootEntries);
        }
        VirtualFile[] sourceRoots = orderEntry.getFiles(OrderRootType.SOURCES);
        for (VirtualFile sourceRoot : sourceRoots) {
          addEntryToMap(sourceRoot, orderEntry, libSourceRootEntries);
        }
      }
    }

    for (Map.Entry<VirtualFile, List<OrderEntry>> mapEntry : depEntries.entrySet()) {
      final VirtualFile vRoot = mapEntry.getKey();
      final List<OrderEntry> entries = mapEntry.getValue();
      fillMapWithOrderEntries(vRoot, entries, null, null, null, forDir, null, null);
    }

    for (Map.Entry<VirtualFile, List<OrderEntry>> mapEntry : libClassRootEntries.entrySet()) {
      final VirtualFile vRoot = mapEntry.getKey();
      final List<OrderEntry> entries = mapEntry.getValue();
      fillMapWithOrderEntries(vRoot, entries, null, vRoot, null, forDir, null, null);
    }

    for (Map.Entry<VirtualFile, List<OrderEntry>> mapEntry : libSourceRootEntries.entrySet()) {
      final VirtualFile vRoot = mapEntry.getKey();
      final List<OrderEntry> entries = mapEntry.getValue();
      fillMapWithOrderEntries(vRoot, entries, null, null, vRoot, forDir, null, null);
    }
  }

  private void addEntryToMap(final VirtualFile vRoot, final OrderEntry entry, final Map<VirtualFile, List<OrderEntry>> map) {
    List<OrderEntry> list = map.get(vRoot);
    if (list == null) {
      list = new ArrayList<OrderEntry>();
      map.put(vRoot, list);
    }
    list.add(entry);
  }

  private void fillMapWithOrderEntries(VirtualFile dir,
                                       List<OrderEntry> orderEntries,
                                       Module module,
                                       VirtualFile libraryClassRoot,
                                       VirtualFile librarySourceRoot,
                                       VirtualFile forDir,
                                       DirectoryInfo parentInfo,
                                       final List<OrderEntry> oldParentEntries) {
    if (isIgnored(dir)) return;

    if (forDir != null) {
      if (!VfsUtil.isAncestor(dir, forDir, false)) return;
    }

    DirectoryInfo info = myDirToInfoMap.get(dir); // do not create it here!
    if (info == null) return;

    if (module != null) {
      if (info.module != module) return;
      if (!info.isInModuleSource) return;
    }
    else if (libraryClassRoot != null) {
      if (info.libraryClassRoot != libraryClassRoot) return;
      if (info.isInModuleSource) return;
    }
    else if (librarySourceRoot != null) {
      if (!info.isInLibrarySource) return;
      if (info.sourceRoot != librarySourceRoot) return;
      if (info.libraryClassRoot != null) return;
    }

    final List<OrderEntry> oldEntries = info.getOrderEntries();
    info.addOrderEntries(orderEntries, parentInfo, oldParentEntries);

    final VirtualFile[] children = dir.getChildren();
    for (VirtualFile child : children) {
      if (child.isDirectory()) {
        fillMapWithOrderEntries(child, orderEntries, module, libraryClassRoot, librarySourceRoot, forDir, info, oldEntries);
      }
    }
  }

  private boolean isIgnored(VirtualFile f) {
    return FileTypeManager.getInstance().isFileIgnored(f.getName());
  }

  public DirectoryInfo getInfoForDirectory(VirtualFile dir) {
    checkAvailability();
    dispatchPendingEvents();

    if (myIsLasyMode) {
      DirectoryInfo info = myDirToInfoMap.get(dir);
      if (info != null) return info;
      doInitialize(false, dir);
    }

    return myDirToInfoMap.get(dir);
  }

  private PackageSink mySink = new PackageSink();

  private class PackageSink extends QueryFactory<VirtualFile, VirtualFile[]> {
    public PackageSink() {
      registerExecutor(new QueryExecutor<VirtualFile, VirtualFile[]>() {
        public boolean execute(final VirtualFile[] allDirs, final Processor<VirtualFile> consumer) {
          for (VirtualFile dir : allDirs) {
            DirectoryInfo info = getInfoForDirectory(dir);
            assert info != null;

            if (!info.isInLibrarySource || info.libraryClassRoot != null) {
              if (!consumer.process(dir)) return false;
            }
          }
          return true;
        }
      });
    }

    public Query<VirtualFile> search(@NotNull String packageName, boolean includeLibrarySources) {
      VirtualFile[] allDirs = doGetDirectoriesByPackageName(packageName);
      if (includeLibrarySources) {
        return new ArrayQuery<VirtualFile>(allDirs);
      }
      else {
        return createQuery(allDirs);
      }
    }
  }

  @NotNull
  public Query<VirtualFile> getDirectoriesByPackageName(@NotNull String packageName, boolean includeLibrarySources) {
    checkAvailability();
    return mySink.search(packageName, includeLibrarySources);
  }

  @NotNull
  private VirtualFile[] doGetDirectoriesByPackageName(@NotNull String packageName) {
    dispatchPendingEvents();

    if (!myIsLasyMode) {
      VirtualFile[] dirs = myPackageNameToDirsMap.get(packageName);
      return dirs != null ? dirs : VirtualFile.EMPTY_ARRAY;
    }
    else {
      VirtualFile[] dirs = myPackageNameToDirsMap.get(packageName);
      if (dirs != null) return dirs;
      dirs = doGetDirectoriesByPackageNameInLazyMode(packageName);
      myPackageNameToDirsMap.put(packageName, dirs);
      return dirs;
    }
  }

  @NotNull
  private VirtualFile[] doGetDirectoriesByPackageNameInLazyMode(@NotNull String packageName) {
    ArrayList<VirtualFile> list = new ArrayList<VirtualFile>();

    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      for (ContentEntry contentEntry : getContentEntries(module)) {
        SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
        for (SourceFolder sourceFolder : sourceFolders) {
          VirtualFile sourceRoot = sourceFolder.getFile();
          if (sourceRoot != null) {
            findAndAddDirByPackageName(list, sourceRoot, packageName);
          }
        }
      }

      for (OrderEntry orderEntry : getOrderEntries(module)) {
        if (orderEntry instanceof LibraryOrderEntry || orderEntry instanceof JdkOrderEntry) {
          VirtualFile[] libRoots = orderEntry.getFiles(OrderRootType.CLASSES);
          for (VirtualFile libRoot : libRoots) {
            findAndAddDirByPackageName(list, libRoot, packageName);
          }

          VirtualFile[] libSourceRoots = orderEntry.getFiles(OrderRootType.SOURCES);
          for (VirtualFile libSourceRoot : libSourceRoots) {
            findAndAddDirByPackageName(list, libSourceRoot, packageName);
          }
        }
      }
    }

    return list.toArray(new VirtualFile[list.size()]);
  }

  private void findAndAddDirByPackageName(ArrayList<VirtualFile> list, VirtualFile root, @NotNull String packageName) {
    VirtualFile dir = findDirByPackageName(root, packageName);
    if (dir == null) return;
    DirectoryInfo info = getInfoForDirectory(dir);
    if (info == null) return;
    if (!packageName.equals(info.packageName)) return;
    if (!list.contains(dir)) {
      list.add(dir);
    }
  }

  private static VirtualFile findDirByPackageName(VirtualFile root, @NotNull String packageName) {
    if (packageName.length() == 0) {
      return root;
    }
    else {
      int index = packageName.indexOf('.');
      if (index < 0) {
        VirtualFile child = root.findChild(packageName);
        if (child == null || !child.isDirectory()) return null;
        return child;
      }
      else {
        String name = packageName.substring(0, index);
        String restName = packageName.substring(index + 1);
        VirtualFile child = root.findChild(name);
        if (child == null || !child.isDirectory()) return null;
        return findDirByPackageName(child, restName);
      }
    }
  }

  private void dispatchPendingEvents() {
    myConnection.deliverImmediately();
    if (myInitialized && PendingEventDispatcher.isDispatchingAnyEvent()) { // optimization
      VirtualFileManager.getInstance().dispatchPendingEvent(myVirtualFileListener);
      //TODO: other listners!!!
    }
  }

  private void checkAvailability() {
    if (!myInitialized) {
      LOG.error("Directory index is not initialized yet.");
    }

    if (myDisposed) {
      LOG.error("Directory index is aleady disposed for this project");
    }
  }

  private DirectoryInfo getOrCreateDirInfo(VirtualFile dir) {
    DirectoryInfo info = myDirToInfoMap.get(dir);
    if (info == null) {
      info = new DirectoryInfo(dir);
      myDirToInfoMap.put(dir, info);
    }
    return info;
  }

  private void setPackageName(VirtualFile dir, DirectoryInfo info, String newPackageName) {
    assert dir != null;

    if (!myIsLasyMode) {
      String oldPackageName = info.packageName;
      if (oldPackageName != null) {
        VirtualFile[] oldPackageDirs = myPackageNameToDirsMap.get(oldPackageName);
        assert oldPackageDirs != null;
        assert oldPackageDirs.length > 0;
        if (oldPackageDirs.length != 1) {
          VirtualFile[] dirs = new VirtualFile[oldPackageDirs.length - 1];

          boolean found = false;
          for (int i = 0; i < oldPackageDirs.length; i++) {
            VirtualFile oldDir = oldPackageDirs[i];
            if (oldDir.equals(dir)) {
              found = true;
              continue;
            }
            dirs[found ? i - 1 : i] = oldDir;
          }

          assert found;

          myPackageNameToDirsMap.put(oldPackageName, dirs);
        }
        else {
          assert dir.equals(oldPackageDirs[0]);
          myPackageNameToDirsMap.remove(oldPackageName);
        }

      }

      if (newPackageName != null) {
        VirtualFile[] newPackageDirs = myPackageNameToDirsMap.get(newPackageName);
        VirtualFile[] dirs;
        if (newPackageDirs == null) {
          dirs = new VirtualFile[]{dir};
        }
        else {
          dirs = new VirtualFile[newPackageDirs.length + 1];
          System.arraycopy(newPackageDirs, 0, dirs, 0, newPackageDirs.length);
          dirs[newPackageDirs.length] = dir;
        }
        myPackageNameToDirsMap.put(newPackageName, dirs);
      }
    }
    else {
      if (info.packageName != null) {
        myPackageNameToDirsMap.remove(info.packageName);
      }
      if (newPackageName != null) {
        myPackageNameToDirsMap.remove(newPackageName);
      }
    }

    info.packageName = newPackageName;
  }

  @Nullable
  private static String getPackageNameForSubdir(String parentPackageName, String subdirName) {
    if (parentPackageName == null) return null;
    return parentPackageName.length() > 0 ? parentPackageName + "." + subdirName : subdirName;
  }

  private class MyVirtualFileListener extends VirtualFileAdapter {
    private final Key<List<VirtualFile>> FILES_TO_RELEASE_KEY = Key.create("DirectoryIndexImpl.MyVirtualFileListener.FILES_TO_RELEASE_KEY");

    public void fileCreated(VirtualFileEvent event) {
      if (myIsLasyMode) return;

      VirtualFile file = event.getFile();

      if (!file.isDirectory()) return;

      VirtualFile parent = file.getParent();
      if (parent == null) return;

      if (isIgnored(file)) return;

      DirectoryInfo parentInfo = myDirToInfoMap.get(parent);
      if (parentInfo == null) return;

      Module module = parentInfo.module;

      for(DirectoryIndexExcludePolicy policy: myExcludePolicies) {
        if (policy.isExcludeRoot(file)) return;
      }

      fillMapWithModuleContent(file, module, parentInfo.contentRoot, null);

      if (module != null) {
        if (parentInfo.isInModuleSource) {
          String newDirPackageName = getPackageNameForSubdir(parentInfo.packageName, file.getName());
          fillMapWithModuleSource(file, module, newDirPackageName, parentInfo.sourceRoot, parentInfo.isTestSource, null);
        }
      }

      if (parentInfo.libraryClassRoot != null) {
        String newDirPackageName = getPackageNameForSubdir(parentInfo.packageName, file.getName());
        fillMapWithLibraryClasses(file, newDirPackageName, parentInfo.libraryClassRoot, null);
      }

      if (parentInfo.isInLibrarySource) {
        String newDirPackageName = getPackageNameForSubdir(parentInfo.packageName, file.getName());
        fillMapWithLibrarySources(file, newDirPackageName, parentInfo.sourceRoot, null);
      }

      if (!parentInfo.getOrderEntries().isEmpty()) {
        fillMapWithOrderEntries(file, parentInfo.getOrderEntries(), null, null, null, null, parentInfo, null);
      }
    }

    public void beforeFileDeletion(VirtualFileEvent event) {
      VirtualFile file = event.getFile();
      if (!file.isDirectory()) return;
      if (!myDirToInfoMap.containsKey(file)) return;

      ArrayList<VirtualFile> list = new ArrayList<VirtualFile>();
      addDirsRecursively(list, file);
      file.putUserData(FILES_TO_RELEASE_KEY, list);
    }

    private void addDirsRecursively(ArrayList<VirtualFile> list, VirtualFile dir) {
      if (!myDirToInfoMap.containsKey(dir) || !(dir instanceof NewVirtualFile)) return;

      list.add(dir);

      for (VirtualFile child : ((NewVirtualFile)dir).getCachedChildren()) {
        if (child.isDirectory()) {
          addDirsRecursively(list, child);
        }
      }
    }

    public void fileDeleted(VirtualFileEvent event) {
      VirtualFile file = event.getFile();
      List<VirtualFile> list = file.getUserData(FILES_TO_RELEASE_KEY);
      if (list == null) return;

      for (VirtualFile dir : list) {
        DirectoryInfo info = myDirToInfoMap.remove(dir);
        if (info != null) {
          setPackageName(dir, info, null);
        }
      }
    }

    public void fileMoved(VirtualFileMoveEvent event) {
      VirtualFile file = event.getFile();

      if (file.isDirectory()) {
        doInitialize();
      }
    }

    public void propertyChanged(VirtualFilePropertyEvent event) {
      if (VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
        VirtualFile file = event.getFile();

        if (file.isDirectory()) {
          doInitialize();
        }
      }
    }
  }
}
