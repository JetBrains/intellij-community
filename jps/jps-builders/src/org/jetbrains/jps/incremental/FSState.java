package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.CompilerExcludes;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.incremental.storage.TimestampStorage;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 12/16/11
 */
public class FSState {
  private final Map<Module, FilesDelta> myDeltas = Collections.synchronizedMap(new HashMap<Module, FilesDelta>());
  private final Set<Module> myInitialTestsScanPerformed = Collections.synchronizedSet(new HashSet<Module>());
  private final Set<Module> myInitialProductionScanPerformed = Collections.synchronizedSet(new HashSet<Module>());

  private final Set<Module> myContextModules = new HashSet<Module>();
  private volatile FilesDelta myCurrentRoundDelta;
  private volatile FilesDelta myLastRoundDelta;

  // when true, will always determine dirty files by scanning FS and comparing timestamps
  // alternatively, when false, after first scan will rely on extarnal notifications about changes
  private final boolean myAlwaysScanFS;

  public FSState(boolean alwaysScanFS) {
    myAlwaysScanFS = alwaysScanFS;
  }

  public void onRebuild() {
    clearContextRoundData();
    myInitialProductionScanPerformed.clear();
    myInitialTestsScanPerformed.clear();
    myDeltas.clear();
  }

  public boolean markInitialScanPerformed(Module module, boolean forTests) {
    if (myAlwaysScanFS) {
      return true;
    }
    final Set<Module> map = forTests ? myInitialTestsScanPerformed : myInitialProductionScanPerformed;
    return map.add(module);
  }

  public void setContextChunk(ModuleChunk chunk) {
    myContextModules.addAll(chunk.getModules());
  }

  public void clearContextChunk() {
    myContextModules.clear();
  }

  public void beforeNextRoundStart() {
    myLastRoundDelta = myCurrentRoundDelta;
    myCurrentRoundDelta = new FilesDelta();
  }

  public void clearContextRoundData() {
    myCurrentRoundDelta = null;
    myLastRoundDelta = null;
  }

  public void clearRecompile(RootDescriptor rd) {
    getDelta(rd.module).clearRecompile(rd.root, rd.isTestRoot);
  }

  public void markDirty(final File file, final RootDescriptor rd, final @Nullable TimestampStorage tsStorage) throws IOException {
    final FilesDelta roundDelta = myCurrentRoundDelta;
    if (roundDelta != null) {
      if (myContextModules.contains(rd.module)) {
        roundDelta.markRecompile(rd.root, rd.isTestRoot, file);
      }
    }
    final FilesDelta mainDelta = getDelta(rd.module);
    final boolean marked = mainDelta.markRecompile(rd.root, rd.isTestRoot, file);
    if (marked && tsStorage != null) {
      tsStorage.markDirty(file);
    }
  }

  /**
   * @return true if marked something, false otherwise
   */
  public boolean markAllUpToDate(CompileScope scope, final RootDescriptor rd, final TimestampStorage tsStorage, final long compilationStartStamp) throws IOException {
    boolean marked = false;
    final FilesDelta delta = getDelta(rd.module);
    final Set<File> files = delta.clearRecompile(rd.root, rd.isTestRoot);
    if (files != null) {
      final CompilerExcludes excludes = rd.module.getProject().getCompilerConfiguration().getExcludes();
      for (File file : files) {
        if (!excludes.isExcluded(file)) {
          if (scope.isAffected(rd.module, file)) {
            final long stamp = file.lastModified();
            if (stamp > compilationStartStamp) {
              // if the file was modified after the compilation had started,
              // do not save the stamp considering file dirty
              delta.markRecompile(rd.root, rd.isTestRoot, file);
            }
            else {
              marked = true;
              tsStorage.saveStamp(file, stamp);
            }
          }
          else {
            delta.markRecompile(rd.root, rd.isTestRoot, file);
          }
        }
        else {
          tsStorage.remove(file);
        }
      }
    }
    return marked;
  }

  public boolean processFilesToRecompile(CompileContext context, final Module module, final FileProcessor processor) throws IOException {
    final FilesDelta lastRoundDelta = myLastRoundDelta;
    final FilesDelta delta = lastRoundDelta != null? lastRoundDelta : getDelta(module);
    final Map<File, Set<File>> data = delta.getSourcesToRecompile(context.isCompilingTests());
    final CompilerExcludes excludes = module.getProject().getCompilerConfiguration().getExcludes();
    final CompileScope scope = context.getScope();
    synchronized (data) {
      for (Map.Entry<File, Set<File>> entry : data.entrySet()) {
        final String root = FileUtil.toSystemIndependentName(entry.getKey().getPath());
        for (File file : entry.getValue()) {
          if (!scope.isAffected(module, file)) {
            continue;
          }
          if (excludes.isExcluded(file)) {
            continue;
          }
          if (!processor.apply(module, file, root)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  public void registerDeleted(final Module module, final File file, final boolean isTest, @Nullable TimestampStorage tsStorage) throws IOException {
    getDelta(module).addDeleted(file, isTest);
    if (tsStorage != null) {
      tsStorage.remove(file);
    }
  }

  public Collection<String> getDeletedPaths(final Module module, final boolean isTest) {
    final FilesDelta delta = myDeltas.get(module);
    if (delta == null) {
      return Collections.emptyList();
    }
    return delta.getDeletedPaths(isTest);
  }

  public void clearDeletedPaths(final Module module, final boolean isTest) {
    final FilesDelta delta = myDeltas.get(module);
    if (delta != null) {
      delta.clearDeletedPaths(isTest);
    }
  }

  @NotNull
  private FilesDelta getDelta(Module module) {
    synchronized (myDeltas) {
      FilesDelta delta = myDeltas.get(module);
      if (delta == null) {
        delta = new FilesDelta();
        myDeltas.put(module, delta);
      }
      return delta;
    }
  }

  /** @noinspection SynchronizationOnLocalVariableOrMethodParameter*/
  private static final class FilesDelta {
    private final Set<String> myDeletedProduction = Collections.synchronizedSet(new HashSet<String>());
    private final Set<String> myDeletedTests = Collections.synchronizedSet(new HashSet<String>());
    private final Map<File, Set<File>> mySourcesToRecompile = Collections.synchronizedMap(new HashMap<File, Set<File>>()); // srcRoot -> set of sources
    private final Map<File, Set<File>> myTestsToRecompile = Collections.synchronizedMap(new HashMap<File, Set<File>>());   // srcRoot -> set of sources

    public boolean markRecompile(File root, boolean isTestRoot, File file) {
      final Map<File, Set<File>> toRecompile = isTestRoot ? myTestsToRecompile : mySourcesToRecompile;
      Set<File> files;
      synchronized (toRecompile) {
        files = toRecompile.get(root);
        if (files == null) {
          files = new HashSet<File>();
          toRecompile.put(root, files);
        }
      }
      final boolean added = files.add(file);
      if (added) {
        final Set<String> deleted = isTestRoot? myDeletedTests : myDeletedProduction;
        deleted.remove(FileUtil.toCanonicalPath(file.getPath()));
      }
      return added;
    }

    public void addDeleted(File file, boolean isTest) {
      // ensure the file is no more marked to recompilation
      final Map<File, Set<File>> toRecompile = isTest ? myTestsToRecompile : mySourcesToRecompile;
      synchronized (toRecompile) {
        for (Set<File> files : toRecompile.values()) {
          files.remove(file);
        }
      }
      final Set<String> deleted = isTest? myDeletedTests : myDeletedProduction;
      deleted.add(FileUtil.toCanonicalPath(file.getPath()));
    }

    public void clearDeletedPaths(boolean isTest) {
      final Set<String> deleted = isTest? myDeletedTests : myDeletedProduction;
      deleted.clear();
    }

    public Map<File, Set<File>> getSourcesToRecompile(boolean forTests) {
      return forTests? myTestsToRecompile : mySourcesToRecompile;
    }

    public Set<String> getDeletedPaths(boolean isTest) {
      final Set<String> deleted = isTest ? myDeletedTests : myDeletedProduction;
      synchronized (deleted) {
        return deleted.isEmpty()? Collections.<String>emptySet() : new HashSet<String>(deleted);
      }
    }

    @Nullable
    public Set<File> clearRecompile(File root, boolean isTestRoot) {
      return isTestRoot? myTestsToRecompile.remove(root) : mySourcesToRecompile.remove(root);
    }
  }
}
