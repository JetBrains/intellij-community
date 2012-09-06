package org.jetbrains.jps.incremental.fs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.artifacts.ArtifactFilesDelta;
import org.jetbrains.jps.incremental.artifacts.ArtifactSourceTimestampStorage;
import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactRootDescriptor;
import org.jetbrains.jps.incremental.storage.Timestamps;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JpsCompilerExcludes;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: 12/16/11
 */
public class BuildFSState extends FSState {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.fs.BuildFSState");
  private static final Key<Set<ModuleBuildTarget>> CONTEXT_TARGETS_KEY = Key.create("_fssfate_context_modules_");
  private static final Key<FilesDelta> CURRENT_ROUND_DELTA_KEY = Key.create("_current_round_delta_");
  private static final Key<FilesDelta> LAST_ROUND_DELTA_KEY = Key.create("_last_round_delta_");

  // when true, will always determine dirty files by scanning FS and comparing timestamps
  // alternatively, when false, after first scan will rely on external notifications about changes
  private final boolean myAlwaysScanFS;

  public BuildFSState(boolean alwaysScanFS) {
    myAlwaysScanFS = alwaysScanFS;
  }

  @Override
  public boolean markInitialScanPerformed(ModuleBuildTarget target) {
    return myAlwaysScanFS || super.markInitialScanPerformed(target);
  }

  @Override
  public boolean markInitialScanPerformed(String artifactName) {
    return myAlwaysScanFS || super.markInitialScanPerformed(artifactName);
  }

  @Override
  public Map<File, Set<File>> getSourcesToRecompile(@NotNull CompileContext context, ModuleBuildTarget target) {
    final FilesDelta lastRoundDelta = getRoundDelta(LAST_ROUND_DELTA_KEY, context);
    if (lastRoundDelta != null) {
      return lastRoundDelta.getSourcesToRecompile();
    }
    return super.getSourcesToRecompile(context, target);
  }

  public Map<Integer, Set<String>> getFilesToRecompile(String artifactName) {
    return getDelta(artifactName).getFilesToRecompile();
  }

  @Override
  public boolean markDirty(@Nullable CompileContext context, File file, final RootDescriptor rd, @Nullable Timestamps tsStorage) throws IOException {
    final FilesDelta roundDelta = getRoundDelta(CURRENT_ROUND_DELTA_KEY, context);
    if (roundDelta != null && isInCurrentContextTargets(context, rd)) {
      roundDelta.markRecompile(rd.root, file);
    }
    return super.markDirty(context, file, rd, tsStorage);
  }

  private static boolean isInCurrentContextTargets(CompileContext context, RootDescriptor rd) {
    if (context == null) {
      return false;
    }
    Set<? extends ModuleBuildTarget> targets = CONTEXT_TARGETS_KEY.get(context, Collections.<ModuleBuildTarget>emptySet());
    return targets.contains(rd.target);
  }

  @Override
  public boolean markDirtyIfNotDeleted(@Nullable CompileContext context, File file, final RootDescriptor rd, @Nullable Timestamps tsStorage) throws IOException {
    final boolean marked = super.markDirtyIfNotDeleted(context, file, rd, tsStorage);
    if (marked) {
      final FilesDelta roundDelta = getRoundDelta(CURRENT_ROUND_DELTA_KEY, context);
      if (roundDelta != null) {
        if (isInCurrentContextTargets(context, rd)) {
          roundDelta.markRecompile(rd.root, file);
        }
      }
    }
    return marked;
  }

  public void clearAll() {
    clearContextRoundData(null);
    clearContextChunk(null);
    myInitialScanPerformed.clear();
    super.clearAll();
  }

  public void clearContextRoundData(@Nullable CompileContext context) {
    setRoundDelta(CURRENT_ROUND_DELTA_KEY, context, null);
    setRoundDelta(LAST_ROUND_DELTA_KEY, context, null);
  }

  public void clearContextChunk(@Nullable CompileContext context) {
    setContextTargets(context, null);
  }

  public void beforeChunkBuildStart(@NotNull CompileContext context, ModuleChunk chunk) {
    setContextTargets(context, chunk.getTargets());
  }

  public void beforeNextRoundStart(@NotNull CompileContext context, ModuleChunk chunk) {
    setRoundDelta(LAST_ROUND_DELTA_KEY, context, getRoundDelta(CURRENT_ROUND_DELTA_KEY, context));
    setRoundDelta(CURRENT_ROUND_DELTA_KEY, context, new FilesDelta());
  }

  public boolean processFilesToRecompile(CompileContext context, final ModuleBuildTarget target, final FileProcessor processor) throws IOException {
    final Map<File, Set<File>> data = getSourcesToRecompile(context, target);
    JpsProject project = context.getProjectDescriptor().jpsProject;
    final JpsCompilerExcludes excludes = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(project).getCompilerExcludes();
    final CompileScope scope = context.getScope();
    synchronized (data) {
      for (Map.Entry<File, Set<File>> entry : data.entrySet()) {
        final String root = FileUtil.toSystemIndependentName(entry.getKey().getPath());
        for (File file : entry.getValue()) {
          if (!scope.isAffected(target, file)) {
            continue;
          }
          if (excludes.isExcluded(file)) {
            continue;
          }
          if (!processor.apply(target, file, root)) {
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
    final FilesDelta delta = getDelta(rd.target);
    final Set<File> files = delta.clearRecompile(rd.root);
    if (files != null) {
      final JpsCompilerExcludes excludes = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(scope.getJpsProject()).getCompilerExcludes();
      for (File file : files) {
        if (!excludes.isExcluded(file)) {
          if (scope.isAffected(rd.target, file)) {
            final long stamp = FileSystemUtil.lastModified(file);
            if (!rd.isGeneratedSources && stamp > compilationStartStamp) {
              // if the file was modified after the compilation had started,
              // do not save the stamp considering file dirty
              delta.markRecompile(rd.root, file);
              if (Utils.IS_TEST_MODE) {
                LOG.info("Timestamp after compilation started; marking dirty again: " + file.getPath());
              }
            }
            else {
              marked = true;
              stamps.saveStamp(file, stamp);
            }
          }
          else {
            if (Utils.IS_TEST_MODE) {
              LOG.info("Not affected by compile scope; marking dirty again: " + file.getPath());
            }
            delta.markRecompile(rd.root, file);
          }
        }
        else {
          stamps.removeStamp(file);
        }
      }
    }
    return marked;
  }

  public boolean markAllUpToDate(ArtifactRootDescriptor descriptor, ArtifactSourceTimestampStorage storage, long compilationStartStamp)
    throws IOException {
    boolean marked = false;
    ArtifactFilesDelta delta = getDelta(descriptor.getArtifactName());
    Set<String> paths = delta.clearRecompile(descriptor.getRootIndex());
    if (paths != null) {
      for (String path : paths) {
        File file = new File(FileUtil.toSystemDependentName(path));
        long stamp = FileSystemUtil.lastModified(file);
        marked = true;
        storage.update(descriptor.getArtifactId(), path, stamp);
      }
    }
    return marked;
  }

  private static void setContextTargets(@Nullable CompileContext context, @Nullable Set<ModuleBuildTarget> targets) {
    if (context != null) {
      CONTEXT_TARGETS_KEY.set(context, targets);
    }
  }

  @Nullable
  private static FilesDelta getRoundDelta(@NotNull Key<FilesDelta> key, @Nullable CompileContext context) {
    return context != null? key.get(context) : null;
  }

  private static void setRoundDelta(@NotNull Key<FilesDelta> key, @Nullable CompileContext context, @Nullable FilesDelta delta) {
    if (context != null) {
      key.set(context, delta);
    }
  }

}
