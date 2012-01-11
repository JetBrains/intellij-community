package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ether.dependencyView.Mappings;
import org.jetbrains.jps.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.incremental.storage.SourceToOutputMapping;
import org.jetbrains.jps.incremental.storage.TimestampStorage;

import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/17/11
 */
public class CompileContext extends UserDataHolderBase implements MessageHandler{
  private final CompileScope myScope;
  private final boolean myIsMake;
  private final boolean myIsProjectRebuild;
  private final ProjectChunks myProductionChunks;
  private final ProjectChunks myTestChunks;
  private final FSState myFsState;
  private final MessageHandler myDelegateMessageHandler;
  private volatile boolean myCompilingTests = false;
  private final BuildDataManager myDataManager;

  private final Map<File, RootDescriptor> myRootToModuleMap = new HashMap<File, RootDescriptor>();
  private final Map<Module, List<RootDescriptor>> myModuleToRootsMap = new HashMap<Module, List<RootDescriptor>>();

  private final ProjectPaths myProjectPaths;
  private volatile boolean myErrorsFound = false;
  private final long myCompilationStartStamp;
  private final TimestampStorage myTsStorage;

  public CompileContext(String projectName, CompileScope scope,
                        boolean isMake,
                        boolean isProjectRebuild,
                        ProjectChunks productionChunks,
                        ProjectChunks testChunks,
                        FSState fsState, TimestampStorage tsStorage, MessageHandler delegateMessageHandler) throws ProjectBuildException {
    myTsStorage = tsStorage;
    myCompilationStartStamp = System.currentTimeMillis();
    myScope = scope;
    myIsProjectRebuild = isProjectRebuild;
    myIsMake = isProjectRebuild? false : isMake;
    myProductionChunks = productionChunks;
    myTestChunks = testChunks;
    myFsState = fsState;
    myDelegateMessageHandler = delegateMessageHandler;
    myDataManager = new BuildDataManager(projectName);
    final Project project = scope.getProject();
    myProjectPaths = new ProjectPaths(project);
    for (Module module : project.getModules().values()) {
      List<RootDescriptor> moduleRoots = myModuleToRootsMap.get(module);
      if (moduleRoots == null) {
        moduleRoots = new ArrayList<RootDescriptor>();
        myModuleToRootsMap.put(module, moduleRoots);
      }
      for (String r : module.getSourceRoots()) {
        final File root = new File(FileUtil.toCanonicalPath(r));
        final RootDescriptor descriptor = new RootDescriptor(module, root, false);
        myRootToModuleMap.put(root, descriptor);
        moduleRoots.add(descriptor);
      }
      for (String r : module.getTestRoots()) {
        final File root = new File(FileUtil.toCanonicalPath(r));
        final RootDescriptor descriptor = new RootDescriptor(module, root, true);
        myRootToModuleMap.put(root, descriptor);
        moduleRoots.add(descriptor);
      }
    }
  }

  public Project getProject() {
    return myScope.getProject();
  }

  public ProjectPaths getProjectPaths() {
    return myProjectPaths;
  }

  public boolean isMake() {
    return myIsMake;
  }

  public boolean isProjectRebuild() {
    return myIsProjectRebuild;
  }

  public void markDirty(final File file) throws Exception {
    final RootDescriptor descriptor = getModuleAndRoot(file);
    if (descriptor != null) {
      myFsState.markDirty(file, descriptor, myTsStorage);
    }
  }

  public void markDirty(ModuleChunk chunk) throws Exception {
    final Set<Module> modules = chunk.getModules();
    final Set<Module> dirtyModules = new HashSet<Module>(modules);

    // now mark all modules that depend on dirty modules
    final ClasspathKind classpathKind = ClasspathKind.compile(isCompilingTests());
    final ProjectChunks chunks = isCompilingTests()? myTestChunks : myProductionChunks;
    boolean found = false;
    for (ModuleChunk moduleChunk : chunks.getChunkList()) {
      if (!found) {
        if (moduleChunk.equals(chunk)) {
          found = true;
        }
      }
      else {
        MODULES_LOOP: for (final Module module : moduleChunk.getModules()) {
          for (ClasspathItem dependency : module.getClasspath(classpathKind)) {
            if (dependency instanceof Module && dirtyModules.contains((Module)dependency)) {
              dirtyModules.addAll(moduleChunk.getModules());
              break MODULES_LOOP;
            }
          }
        }
      }
    }

    for (Module module : dirtyModules) {
      markDirtyFiles(module, myTsStorage, true, DirtyMarkScope.BOTH, null);
    }
  }

  public Mappings createDelta() {
    return myDataManager.getMappings().createDelta();
  }

  public boolean isCompilingTests() {
    return myCompilingTests;
  }

  void setCompilingTests(boolean compilingTests) {
    myCompilingTests = compilingTests;
  }

  void beforeNextCompileRound(@NotNull ModuleChunk chunk) {
    myFsState.beforeNextRoundStart();
  }

  void onChunkBuildComplete(@NotNull ModuleChunk chunk) throws Exception {
    myDataManager.getMappings().clearMemoryCaches();

    try {
      if (!myErrorsFound) {
        final boolean compilingTests = isCompilingTests();
        for (Module module : chunk.getModules()) {
          final List<RootDescriptor> roots = myModuleToRootsMap.get(module);
          for (RootDescriptor descriptor : roots) {
            if (compilingTests? descriptor.isTestRoot : !descriptor.isTestRoot) {
              myFsState.markAllUpToDate(descriptor, myTsStorage, myCompilationStartStamp);
            }
          }
        }
      }
    }
    finally {
      myFsState.clearRoundDeltas();
    }
  }

  public CompileScope getScope() {
    return myScope;
  }

  public BuildDataManager getDataManager() {
    return myDataManager;
  }

  public void processMessage(BuildMessage msg) {
    if (msg.getKind() == BuildMessage.Kind.ERROR) {
      myErrorsFound = true;
    }
    myDelegateMessageHandler.processMessage(msg);
  }

  public void processFilesToRecompile(ModuleChunk chunk, FileProcessor processor) throws Exception {
    for (Module module : chunk.getModules()) {
      myFsState.processFilesToRecompile(module, isCompilingTests(), processor);
    }
  }

  final void ensureFSStateInitialized(ModuleChunk chunk) throws Exception {
    for (Module module : chunk.getModules()) {
      if (isProjectRebuild()) {
        markDirtyFiles(module, myTsStorage, true, isCompilingTests() ? DirtyMarkScope.TESTS : DirtyMarkScope.PRODUCTION, null);
      }
      else {
        // in 'make' mode
        // todo: consider situation when only several files are forced to be compiled => this is not project rebuild and not make
        if (myFsState.markInitialScanPerformed(module, isCompilingTests())) {
          initModuleFSState(module);
        }
      }
    }
  }

  private void initModuleFSState(Module module) throws Exception {
    final HashSet<File> currentFiles = new HashSet<File>();
    markDirtyFiles(module, myTsStorage, false, isCompilingTests() ? DirtyMarkScope.TESTS : DirtyMarkScope.PRODUCTION, currentFiles);

    final String moduleName = module.getName().toLowerCase(Locale.US);
    final SourceToOutputMapping sourceToOutputMap = getDataManager().getSourceToOutputMap(moduleName, isCompilingTests());
    for (final Iterator<String> it = sourceToOutputMap.getKeysIterator(); it.hasNext();) {
      final String path = it.next();
      // can check if the file exists
      final File file = new File(path);
      if (!currentFiles.contains(file)) {
        myFsState.registerDeleted(module, path, isCompilingTests(), myTsStorage);
      }
    }
  }

  public boolean hasRemovedSources() {
    final Set<String> removed = Paths.CHUNK_REMOVED_SOURCES_KEY.get(this);
    return removed != null && !removed.isEmpty();
  }

  @Nullable
  public RootDescriptor getModuleAndRoot(File file) {
    File current = file;
    while (current != null) {
      final RootDescriptor descriptor = myRootToModuleMap.get(current);
      if (descriptor != null) {
        return descriptor;
      }
      current = FileUtil.getParentFile(current);
    }
    return null;
  }

  @NotNull
  public List<RootDescriptor> getModuleRoots(Module module) {
    final List<RootDescriptor> rootDescriptors = myModuleToRootsMap.get(module);
    return rootDescriptors != null? Collections.unmodifiableList(rootDescriptors) : Collections.<RootDescriptor>emptyList();
  }

  private static enum DirtyMarkScope{
    PRODUCTION, TESTS, BOTH
  }

  private void markDirtyFiles(Module module, final TimestampStorage tsStorage, final boolean forceMarkDirty, @NotNull final DirtyMarkScope scope, @Nullable final Set<File> currentFiles) throws Exception {
    final Set<File> excludes = new HashSet<File>();
    for (String excludePath : module.getExcludes()) {
      excludes.add(new File(excludePath));
    }
    for (RootDescriptor rd : getModuleRoots(module)) {
      if (scope == DirtyMarkScope.TESTS) {
        if (!rd.isTestRoot) {
          continue;
        }
      }
      else if (scope == DirtyMarkScope.PRODUCTION) {
        if (rd.isTestRoot) {
          continue;
        }
      }
      if (!rd.root.exists()) {
        continue;
      }
      if (forceMarkDirty) {
        myFsState.clearRecompile(rd);
        myFsState.clearDeletedPaths(module, isCompilingTests());
      }
      traverseRecursively(rd, rd.root, excludes, tsStorage, forceMarkDirty, currentFiles);
    }
  }

  private void traverseRecursively(final RootDescriptor rd, final File file, Set<File> excludes, @NotNull final TimestampStorage tsStorage, final boolean forceDirty, @Nullable Set<File> currentFiles) throws Exception {
    if (file.isDirectory()) {
      if (!PathUtil.isUnder(excludes, file)) {
        final File[] children = file.listFiles();
        if (children != null) {
          for (File child : children) {
            traverseRecursively(rd, child, excludes, tsStorage, forceDirty, currentFiles);
          }
        }
      }
    }
    else {
      boolean markDirty = forceDirty;
      if (!markDirty) {
        markDirty = tsStorage.getStamp(file) != file.lastModified();
      }
      if (markDirty) {
        // if it is full project rebuild, all storages are already completely cleared;
        // so passing null because there is no need to access the storage to clear non-existing data
        final TimestampStorage _tsStorage = isProjectRebuild() ? null : tsStorage;
        myFsState.markDirty(file, rd, _tsStorage);
      }
      if (currentFiles != null) {
        currentFiles.add(file);
      }
    }
  }
}
