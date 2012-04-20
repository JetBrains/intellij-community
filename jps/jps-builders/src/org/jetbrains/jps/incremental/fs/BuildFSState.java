package org.jetbrains.jps.incremental.fs;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.incremental.RootDescriptor;
import org.jetbrains.jps.incremental.storage.TimestampStorage;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: 12/16/11
 */
public class BuildFSState extends FSState {
  private final Set<Module> myInitialTestsScanPerformed = Collections.synchronizedSet(new HashSet<Module>());
  private final Set<Module> myInitialProductionScanPerformed = Collections.synchronizedSet(new HashSet<Module>());

  private final Set<Module> myContextModules = new HashSet<Module>();
  private volatile FilesDelta myCurrentRoundDelta;
  private volatile FilesDelta myLastRoundDelta;

  // when true, will always determine dirty files by scanning FS and comparing timestamps
  // alternatively, when false, after first scan will rely on extarnal notifications about changes
  private final boolean myAlwaysScanFS;

  public BuildFSState(boolean alwaysScanFS) {
    myAlwaysScanFS = alwaysScanFS;
  }

  public boolean markInitialScanPerformed(Module module, boolean forTests) {
    if (myAlwaysScanFS) {
      return true;
    }
    final Set<Module> map = forTests ? myInitialTestsScanPerformed : myInitialProductionScanPerformed;
    return map.add(module);
  }

  @Override
  public Map<File, Set<File>> getSourcesToRecompile(Module module, boolean forTests) {
    final FilesDelta lastRoundDelta = myLastRoundDelta;
    if (lastRoundDelta != null) {
      return lastRoundDelta.getSourcesToRecompile(forTests);
    }
    return super.getSourcesToRecompile(module, forTests);
  }

  @Override
  public boolean markDirty(File file, RootDescriptor rd, @Nullable TimestampStorage tsStorage) throws IOException {
    final FilesDelta roundDelta = myCurrentRoundDelta;
    if (roundDelta != null) {
      if (myContextModules.contains(rd.module)) {
        roundDelta.markRecompile(rd.root, rd.isTestRoot, file);
      }
    }
    return super.markDirty(file, rd, tsStorage);
  }

  @Override
  public boolean markDirtyIfNotDeleted(File file, RootDescriptor rd, @Nullable TimestampStorage tsStorage) throws IOException {
    final boolean marked = super.markDirtyIfNotDeleted(file, rd, tsStorage);
    if (marked) {
      final FilesDelta roundDelta = myCurrentRoundDelta;
      if (roundDelta != null) {
        if (myContextModules.contains(rd.module)) {
          roundDelta.markRecompile(rd.root, rd.isTestRoot, file);
        }
      }
    }
    return marked;
  }

  public void clearAll() {
    clearContextRoundData();
    clearContextChunk();
    myInitialProductionScanPerformed.clear();
    myInitialTestsScanPerformed.clear();
    super.clearAll();
  }

  public void clearContextRoundData() {
    myCurrentRoundDelta = null;
    myLastRoundDelta = null;
  }

  public void clearContextChunk() {
    myContextModules.clear();
  }

  public void setContextChunk(ModuleChunk chunk) {
    myContextModules.addAll(chunk.getModules());
  }

  public void beforeNextRoundStart() {
    myLastRoundDelta = myCurrentRoundDelta;
    myCurrentRoundDelta = new FilesDelta();
  }

}
