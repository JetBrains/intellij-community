package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ether.dependencyView.Mappings;
import org.jetbrains.jps.*;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.messages.UptoDateFilesSavedEvent;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.incremental.storage.SourceToOutputMapping;
import org.jetbrains.jps.incremental.storage.TimestampStorage;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/17/11
 */
public class CompileContext extends UserDataHolderBase implements MessageHandler{
  private static final String CANCELED_MESSAGE = "The build has been canceled";
  private final CompileScope myScope;
  private final boolean myIsMake;
  private final boolean myIsProjectRebuild;
  private final ProjectChunks myProductionChunks;
  private final ProjectChunks myTestChunks;
  private final FSState myFsState;
  private final MessageHandler myDelegateMessageHandler;
  private volatile boolean myCompilingTests = false;
  private final BuildDataManager myDataManager;
  private final ModuleRootsIndex myRootsIndex;

  private final Set<Pair<Module, DirtyMarkScope>> myNonIncrementalModules = new HashSet<Pair<Module, DirtyMarkScope>>();

  private final ProjectPaths myProjectPaths;
  private volatile boolean myErrorsFound = false;
  private final long myCompilationStartStamp;
  private final TimestampStorage myTsStorage;
  private final CanceledStatus myCancelStatus;
  private float myDone = -1.0f;

  public CompileContext(CompileScope scope,
                        boolean isMake,
                        boolean isProjectRebuild,
                        ProjectChunks productionChunks,
                        ProjectChunks testChunks,
                        FSState fsState, final BuildDataManager dataManager, TimestampStorage tsStorage, MessageHandler delegateMessageHandler, final ModuleRootsIndex rootsIndex, CanceledStatus cancelStatus) throws ProjectBuildException {
    myTsStorage = tsStorage;
    myCancelStatus = cancelStatus;
    myCompilationStartStamp = System.currentTimeMillis();
    myScope = scope;
    myIsProjectRebuild = isProjectRebuild;
    myIsMake = isProjectRebuild? false : isMake;
    myProductionChunks = productionChunks;
    myTestChunks = testChunks;
    myFsState = fsState;
    myDelegateMessageHandler = delegateMessageHandler;
    myDataManager = dataManager;
    final Project project = scope.getProject();
    myProjectPaths = new ProjectPaths(project);
    myRootsIndex = rootsIndex;
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

  public void markDirty(final File file) throws IOException {
    final RootDescriptor descriptor = getModuleAndRoot(file);
    if (descriptor != null) {
      myFsState.markDirty(file, descriptor, myTsStorage);
    }
  }

  public void markDirty(final ModuleChunk chunk) throws IOException {
    myFsState.clearContextRoundData();
    final Set<Module> modules = chunk.getModules();
    for (Module module : modules) {
      markDirtyFiles(module, myTsStorage, true, isCompilingTests()? DirtyMarkScope.TESTS : DirtyMarkScope.PRODUCTION, null);
    }
  }

  public void markDirtyRecursively(ModuleChunk chunk) throws IOException {
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
      markDirtyFiles(module, myTsStorage, true, isCompilingTests()? DirtyMarkScope.TESTS : DirtyMarkScope.BOTH, null);
      if (isMake()) {
        if (!isCompilingTests()) {
          myNonIncrementalModules.add(new Pair<Module, DirtyMarkScope>(module, DirtyMarkScope.PRODUCTION));
        }
        myNonIncrementalModules.add(new Pair<Module, DirtyMarkScope>(module, DirtyMarkScope.TESTS));
      }
    }
  }

  boolean shouldDifferentiate(ModuleChunk chunk, boolean forTests) {
    if (!isMake()) {
      // the check makes sense only in make mode
      return true;
    }
    final DirtyMarkScope dirtyScope = forTests ? DirtyMarkScope.TESTS : DirtyMarkScope.PRODUCTION;
    for (Module module : chunk.getModules()) {
      if (myNonIncrementalModules.contains(new Pair<Module, DirtyMarkScope>(module, dirtyScope))) {
        return false;
      }
    }
    return true;
  }

  public Mappings createDelta() {
    return myDataManager.getMappings().createDelta();
  }

  public boolean isCompilingTests() {
    return myCompilingTests;
  }

  public final CanceledStatus getCancelStatus() {
    return myCancelStatus;
  }

  public final boolean isCanceled() {
    return getCancelStatus().isCanceled();
  }

  public final void checkCanceled() throws ProjectBuildException {
    if (isCanceled()) {
      throw new ProjectBuildException(CANCELED_MESSAGE);
    }
  }

  void setCompilingTests(boolean compilingTests) {
    myCompilingTests = compilingTests;
  }

  void beforeCompileRound(@NotNull ModuleChunk chunk) {
    myFsState.beforeNextRoundStart();
  }

  public void onChunkBuildStart(ModuleChunk chunk) {
    myFsState.setContextChunk(chunk);
  }

  void onChunkBuildComplete(@NotNull ModuleChunk chunk) throws IOException {
    myDataManager.flush(true);
    myFsState.clearContextRoundData();
    myFsState.clearContextChunk();

    if (!myErrorsFound && !myCancelStatus.isCanceled()) {
      final boolean compilingTests = isCompilingTests();
      final DirtyMarkScope dirtyScope = compilingTests ? DirtyMarkScope.TESTS : DirtyMarkScope.PRODUCTION;
      boolean marked = false;
      for (Module module : chunk.getModules()) {
        if (isMake()) {
          // ensure non-incremental flag cleared
          myNonIncrementalModules.remove(new Pair<Module, DirtyMarkScope>(module, dirtyScope));
        }
        if (isProjectRebuild()) {
          myFsState.markInitialScanPerformed(module, compilingTests);
        }
        final List<RootDescriptor> roots = myRootsIndex.getModuleRoots(module);
        for (RootDescriptor descriptor : roots) {
          if (compilingTests? descriptor.isTestRoot : !descriptor.isTestRoot) {
            marked |= myFsState.markAllUpToDate(getScope(), descriptor, myTsStorage, myCompilationStartStamp);
          }
        }
      }
      if (marked) {
        processMessage(UptoDateFilesSavedEvent.INSTANCE);
      }
    }
  }

  public CompileScope getScope() {
    return myScope;
  }

  public BuildDataManager getDataManager() {
    return myDataManager;
  }

  public TimestampStorage getTimestampStorage() {
    return myTsStorage;
  }

  public void processMessage(BuildMessage msg) {
    if (msg.getKind() == BuildMessage.Kind.ERROR) {
      myErrorsFound = true;
    }
    if (msg instanceof ProgressMessage) {
      ((ProgressMessage)msg).setDone(myDone);
    }
    myDelegateMessageHandler.processMessage(msg);
  }

  public void processFilesToRecompile(ModuleChunk chunk, FileProcessor processor) throws IOException {
    for (Module module : chunk.getModules()) {
      myFsState.processFilesToRecompile(this, module, processor);
    }
  }

  final void ensureFSStateInitialized(ModuleChunk chunk) throws IOException {
    for (Module module : chunk.getModules()) {
      if (isProjectRebuild()) {
        markDirtyFiles(module, myTsStorage, true, isCompilingTests() ? DirtyMarkScope.TESTS : DirtyMarkScope.PRODUCTION, null);
      }
      else {
        if (isMake()) {
          if (myFsState.markInitialScanPerformed(module, isCompilingTests())) {
            initModuleFSState(module);
          }
        }
        else {
          // forced compilation mode
          if (getScope().isRecompilationForced(module)) {
            markDirtyFiles(module, myTsStorage, true, isCompilingTests() ? DirtyMarkScope.TESTS : DirtyMarkScope.PRODUCTION, null);
          }
        }
      }
    }
  }

  private void initModuleFSState(Module module) throws IOException {
    final HashSet<File> currentFiles = new HashSet<File>();
    markDirtyFiles(module, myTsStorage, false, isCompilingTests() ? DirtyMarkScope.TESTS : DirtyMarkScope.PRODUCTION, currentFiles);

    final String moduleName = module.getName().toLowerCase(Locale.US);
    final SourceToOutputMapping sourceToOutputMap = getDataManager().getSourceToOutputMap(moduleName, isCompilingTests());
    for (final Iterator<String> it = sourceToOutputMap.getKeysIterator(); it.hasNext();) {
      final String path = it.next();
      // can check if the file exists
      final File file = new File(path);
      if (!currentFiles.contains(file)) {
        myFsState.registerDeleted(module, file, isCompilingTests(), myTsStorage);
      }
    }
  }

  public boolean hasRemovedSources() {
    final Set<String> removed = Paths.CHUNK_REMOVED_SOURCES_KEY.get(this);
    return removed != null && !removed.isEmpty();
  }

  @Nullable
  public RootDescriptor getModuleAndRoot(File file) {
    return myRootsIndex.getModuleAndRoot(file);
  }

  @NotNull
  public List<RootDescriptor> getModuleRoots(Module module) {
    return myRootsIndex.getModuleRoots(module);
  }

  public ModuleRootsIndex getRootsIndex() {
    return myRootsIndex;
  }

  public void setDone(float done) {
    myDone = done;
    //processMessage(new ProgressMessage("", done));
  }

  public static enum DirtyMarkScope{
    PRODUCTION, TESTS, BOTH
  }

  private void markDirtyFiles(Module module, final TimestampStorage tsStorage, final boolean forceMarkDirty, @NotNull final DirtyMarkScope scope, @Nullable final Set<File> currentFiles) throws IOException {
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
      myFsState.clearRecompile(rd);
      myFsState.clearDeletedPaths(module, isCompilingTests());
      traverseRecursively(rd, rd.root, excludes, tsStorage, forceMarkDirty, currentFiles);
    }
  }

  private void traverseRecursively(final RootDescriptor rd, final File file, Set<File> excludes, @NotNull final TimestampStorage tsStorage, final boolean forceDirty, @Nullable Set<File> currentFiles) throws IOException {
    final File[] children = file.listFiles();
    if (children != null) { // is directory
      if (children.length > 0 && !PathUtil.isUnder(excludes, file)) {
        for (File child : children) {
          traverseRecursively(rd, child, excludes, tsStorage, forceDirty, currentFiles);
        }
      }
    }
    else { // is file
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
