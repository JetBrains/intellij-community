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
import org.jetbrains.jps.server.ProjectDescriptor;

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
  private final MessageHandler myDelegateMessageHandler;
  private volatile boolean myCompilingTests = false;
  private final Set<Pair<Module, DirtyMarkScope>> myNonIncrementalModules = new HashSet<Pair<Module, DirtyMarkScope>>();

  private final ProjectPaths myProjectPaths;
  private volatile boolean myErrorsFound = false;
  private final long myCompilationStartStamp;
  private final ProjectDescriptor myProjectDescriptor;
  private final TimestampStorage myTsStorage;
  private final Map<String, String> myBuilderParams;
  private final CanceledStatus myCancelStatus;
  private float myDone = -1.0f;

  public CompileContext(CompileScope scope,
                        ProjectDescriptor pd, boolean isMake,
                        boolean isProjectRebuild,
                        ProjectChunks productionChunks,
                        ProjectChunks testChunks,
                        MessageHandler delegateMessageHandler,
                        Map<String, String> builderParams,
                        CanceledStatus cancelStatus) throws ProjectBuildException {
    myProjectDescriptor = pd;
    myTsStorage = myProjectDescriptor.timestamps.getStorage();
    myBuilderParams = Collections.unmodifiableMap(builderParams);
    myCancelStatus = cancelStatus;
    myCompilationStartStamp = System.currentTimeMillis();
    myScope = scope;
    myIsProjectRebuild = isProjectRebuild;
    myIsMake = !isProjectRebuild && isMake;
    myProductionChunks = productionChunks;
    myTestChunks = testChunks;
    myDelegateMessageHandler = delegateMessageHandler;
    final Project project = scope.getProject();
    myProjectPaths = new ProjectPaths(project);
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

  public BuildLoggingManager getLoggingManager() {
    return myProjectDescriptor.getLoggingManager();
  }

  @Nullable
  public String getBuilderParameter(String paramName) {
    return myBuilderParams.get(paramName);
  }

  public void markDirty(final File file) throws IOException {
    final RootDescriptor descriptor = getModuleAndRoot(file);
    if (descriptor != null) {
      myProjectDescriptor.fsState.markDirty(file, descriptor, myTsStorage);
    }
  }

  public void markDirtyIfNotDeleted(final File file) throws IOException {
    final RootDescriptor descriptor = getModuleAndRoot(file);
    if (descriptor != null) {
      myProjectDescriptor.fsState.markDirtyIfNotDeleted(file, descriptor, myTsStorage);
    }
  }

  public void markDeleted(File file) throws IOException {
    final RootDescriptor descriptor = getModuleAndRoot(file);
    if (descriptor != null) {
      myProjectDescriptor.fsState.registerDeleted(descriptor.module, file, descriptor.isTestRoot, myTsStorage);
    }
  }

  public void markDirty(final ModuleChunk chunk) throws IOException {
    myProjectDescriptor.fsState.clearContextRoundData();
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
        for (final Module module : moduleChunk.getModules()) {
          final Set<Module> deps = getDependentModulesRecursively(module, classpathKind);
          if (Utils.intersects(deps, modules)) {
            dirtyModules.addAll(moduleChunk.getModules());
            break;
          }
        }
      }
    }

    for (Module module : dirtyModules) {
      markDirtyFiles(module, myTsStorage, true, isCompilingTests()? DirtyMarkScope.TESTS : DirtyMarkScope.BOTH, null);
    }

    if (isMake()) {
      // mark as non-incremental only the module that triggered non-incremental change
      for (Module module : modules) {
        if (!isCompilingTests()) {
          myNonIncrementalModules.add(new Pair<Module, DirtyMarkScope>(module, DirtyMarkScope.PRODUCTION));
        }
        myNonIncrementalModules.add(new Pair<Module, DirtyMarkScope>(module, DirtyMarkScope.TESTS));
      }
    }
  }

  private static Set<Module> getDependentModulesRecursively(final Module module, final ClasspathKind kind) {
    final Set<Module> result = new HashSet<Module>();

    new Object() {
      final Set<Module> processed = new HashSet<Module>();

      void traverse(Module module, ClasspathKind kind, Collection<Module> result, boolean exportedOnly) {
        if (processed.add(module)) {
          for (ClasspathItem item : module.getClasspath(kind, exportedOnly)) {
            if (item instanceof ModuleSourceEntry) {
              result.add(((ModuleSourceEntry)item).getModule());
            }
            else if (item instanceof Module) {
              traverse((Module)item, kind, result, true);
            }
          }
        }
      }

    }.traverse(module, kind, result, false);

    return result;
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
    return getDataManager().getMappings().createDelta();
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
    myProjectDescriptor.fsState.beforeNextRoundStart();
  }

  public void onChunkBuildStart(ModuleChunk chunk) {
    myProjectDescriptor.fsState.setContextChunk(chunk);
  }

  void onChunkBuildComplete(@NotNull ModuleChunk chunk) throws IOException {
    getDataManager().closeSourceToOutputStorages(chunk, isCompilingTests());
    getDataManager().flush(true);
    myProjectDescriptor.fsState.clearContextRoundData();
    myProjectDescriptor.fsState.clearContextChunk();

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
          myProjectDescriptor.fsState.markInitialScanPerformed(module, compilingTests);
        }
        final List<RootDescriptor> roots = myProjectDescriptor.rootsIndex.getModuleRoots(module);
        for (RootDescriptor descriptor : roots) {
          if (compilingTests? descriptor.isTestRoot : !descriptor.isTestRoot) {
            marked |= myProjectDescriptor.fsState.markAllUpToDate(getScope(), descriptor, myTsStorage, myCompilationStartStamp);
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
    return myProjectDescriptor.dataManager;
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

  public boolean errorsDetected() {
    return myErrorsFound;
  }

  public void processFilesToRecompile(ModuleChunk chunk, FileProcessor processor) throws IOException {
    for (Module module : chunk.getModules()) {
      myProjectDescriptor.fsState.processFilesToRecompile(this, module, processor);
    }
  }

  final void ensureFSStateInitialized(ModuleChunk chunk) throws IOException {
    for (Module module : chunk.getModules()) {
      if (isProjectRebuild()) {
        markDirtyFiles(module, myTsStorage, true, isCompilingTests() ? DirtyMarkScope.TESTS : DirtyMarkScope.PRODUCTION, null);
      }
      else {
        if (isMake()) {
          if (myProjectDescriptor.fsState.markInitialScanPerformed(module, isCompilingTests())) {
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
        myProjectDescriptor.fsState.registerDeleted(module, file, isCompilingTests(), myTsStorage);
      }
    }
  }

  public boolean hasRemovedSources() {
    final Set<String> removed = Utils.CHUNK_REMOVED_SOURCES_KEY.get(this);
    return removed != null && !removed.isEmpty();
  }

  @Nullable
  public RootDescriptor getModuleAndRoot(File file) {
    return getRootsIndex().getModuleAndRoot(file);
  }

  @NotNull
  public List<RootDescriptor> getModuleRoots(Module module) {
    return getRootsIndex().getModuleRoots(module);
  }

  public ModuleRootsIndex getRootsIndex() {
    return myProjectDescriptor.rootsIndex;
  }

  public void setDone(float done) {
    myDone = done;
    //processMessage(new ProgressMessage("", done));
  }

  public ProjectDescriptor getProjectDescriptor() {
    return myProjectDescriptor;
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
      myProjectDescriptor.fsState.clearRecompile(rd);
      myProjectDescriptor.fsState.clearDeletedPaths(module, isCompilingTests());
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
        myProjectDescriptor.fsState.markDirty(file, rd, _tsStorage);
      }
      if (currentFiles != null) {
        currentFiles.add(file);
      }
    }
  }
}
