package org.jetbrains.jps.incremental.fs;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.CompilerExcludes;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.CompileScope;
import org.jetbrains.jps.incremental.FileProcessor;
import org.jetbrains.jps.incremental.storage.Timestamps;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: 12/16/11
 */
public class BuildFSState extends FSState {

  private final Set<String> myContextModules = new HashSet<String>();
  private volatile FilesDelta myCurrentRoundDelta;
  private volatile FilesDelta myLastRoundDelta;

  // when true, will always determine dirty files by scanning FS and comparing timestamps
  // alternatively, when false, after first scan will rely on extarnal notifications about changes
  private final boolean myAlwaysScanFS;

  public BuildFSState(boolean alwaysScanFS) {
    myAlwaysScanFS = alwaysScanFS;
  }

  @Override
  public boolean isInitialized(String moduleName) {
    return myAlwaysScanFS || super.isInitialized(moduleName);
  }

  @Override
  public boolean markInitialScanPerformed(String moduleName, boolean forTests) {
    return myAlwaysScanFS || super.markInitialScanPerformed(moduleName, forTests);
  }

  @Override
  public Map<File, Set<File>> getSourcesToRecompile(final String moduleName, boolean forTests) {
    final FilesDelta lastRoundDelta = myLastRoundDelta;
    if (lastRoundDelta != null) {
      return lastRoundDelta.getSourcesToRecompile(forTests);
    }
    return super.getSourcesToRecompile(moduleName, forTests);
  }

  @Override
  public boolean markDirty(File file, final RootDescriptor rd, @Nullable Timestamps tsStorage) throws IOException {
    final FilesDelta roundDelta = myCurrentRoundDelta;
    if (roundDelta != null) {
      if (myContextModules.contains(rd.module)) {
        roundDelta.markRecompile(rd.root, rd.isTestRoot, file);
      }
    }
    return super.markDirty(file, rd, tsStorage);
  }

  @Override
  public boolean markDirtyIfNotDeleted(File file, final RootDescriptor rd, @Nullable Timestamps tsStorage) throws IOException {
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
    myContextModules.clear();
    for (Module module : chunk.getModules()) {
      myContextModules.add(module.getName());
    }
  }

  public void beforeNextRoundStart() {
    myLastRoundDelta = myCurrentRoundDelta;
    myCurrentRoundDelta = new FilesDelta();
  }

  public boolean processFilesToRecompile(CompileContext context, final Module module, final FileProcessor processor) throws IOException {
    final String moduleName = module.getName();
    final Map<File, Set<File>> data = getSourcesToRecompile(moduleName, context.isCompilingTests());
    final CompilerExcludes excludes = context.getProject().getCompilerConfiguration().getExcludes();
    final CompileScope scope = context.getScope();
    synchronized (data) {
      for (Map.Entry<File, Set<File>> entry : data.entrySet()) {
        final String root = FileUtil.toSystemIndependentName(entry.getKey().getPath());
        for (File file : entry.getValue()) {
          if (!scope.isAffected(moduleName, file)) {
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

  /**
   * @return true if marked something, false otherwise
   */
  public boolean markAllUpToDate(CompileScope scope, final RootDescriptor rd, final Timestamps stamps, final long compilationStartStamp) throws IOException {
    boolean marked = false;
    final FilesDelta delta = getDelta(rd.module);
    final Set<File> files = delta.clearRecompile(rd.root, rd.isTestRoot);
    if (files != null) {
      final CompilerExcludes excludes = scope.getProject().getCompilerConfiguration().getExcludes();
      for (File file : files) {
        if (!excludes.isExcluded(file)) {
          if (scope.isAffected(rd.module, file)) {
            final long stamp = file.lastModified();
            if (!rd.isGeneratedSources && stamp > compilationStartStamp) {
              // if the file was modified after the compilation had started,
              // do not save the stamp considering file dirty
              delta.markRecompile(rd.root, rd.isTestRoot, file);
            }
            else {
              marked = true;
              stamps.saveStamp(file, stamp);
            }
          }
          else {
            delta.markRecompile(rd.root, rd.isTestRoot, file);
          }
        }
        else {
          stamps.removeStamp(file);
        }
      }
    }
    return marked;
  }
}
