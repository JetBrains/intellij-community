package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileSystemUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.JpsPathUtil;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.ProjectChunks;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.fs.BuildFSState;
import org.jetbrains.jps.incremental.fs.RootDescriptor;
import org.jetbrains.jps.incremental.storage.Timestamps;
import org.jetbrains.jps.model.java.JpsJavaClasspathKind;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: 7/8/12
 */
public class FSOperations {
  public static void markDirty(CompileContext context, final File file) throws IOException {
    final RootDescriptor rd = context.getProjectDescriptor().rootsIndex.getModuleAndRoot(context, file);
    if (rd != null) {
      final ProjectDescriptor pd = context.getProjectDescriptor();
      pd.fsState.markDirty(context, file, rd, pd.timestamps.getStorage());
    }
  }

  public static void markDirtyIfNotDeleted(CompileContext context, final File file) throws IOException {
    final RootDescriptor rd = context.getProjectDescriptor().rootsIndex.getModuleAndRoot(context, file);
    if (rd != null) {
      final ProjectDescriptor pd = context.getProjectDescriptor();
      pd.fsState.markDirtyIfNotDeleted(context, file, rd, pd.timestamps.getStorage());
    }
  }

  public static void markDeleted(CompileContext context, File file) throws IOException {
    final RootDescriptor rd = context.getProjectDescriptor().rootsIndex.getModuleAndRoot(context, file);
    if (rd != null) {
      final ProjectDescriptor pd = context.getProjectDescriptor();
      pd.fsState.registerDeleted(rd.target, file, pd.timestamps.getStorage());
    }
  }

  public static void markDirty(CompileContext context, final ModuleChunk chunk) throws IOException {
    final ProjectDescriptor pd = context.getProjectDescriptor();
    pd.fsState.clearContextRoundData(context);
    for (RealModuleBuildTarget target : chunk.getTargets()) {
      markDirtyFiles(context, target, pd.timestamps.getStorage(), true, target.isTests() ? DirtyMarkScope.TESTS : DirtyMarkScope.PRODUCTION, null);
    }
  }

  public static void markDirtyRecursively(CompileContext context, ModuleChunk chunk) throws IOException {
    Set<JpsModule> modules = chunk.getModules();
    Set<RealModuleBuildTarget> targets = chunk.getTargets();
    final Set<RealModuleBuildTarget> dirtyTargets = new HashSet<RealModuleBuildTarget>(targets);

    // now mark all modules that depend on dirty modules
    final JpsJavaClasspathKind classpathKind = JpsJavaClasspathKind.compile(chunk.isTests());
    final ProjectChunks chunks = chunk.isTests()? context.getTestChunks() : context.getProductionChunks();
    boolean found = false;
    for (ModuleChunk moduleChunk : chunks.getChunkList()) {
      if (!found) {
        if (moduleChunk.equals(chunk)) {
          found = true;
        }
      }
      else {
        for (final JpsModule module : moduleChunk.getModules()) {
          final Set<JpsModule> deps = getDependentModulesRecursively(module, classpathKind);
          if (Utils.intersects(deps, modules)) {
            dirtyTargets.addAll(moduleChunk.getTargets());
            break;
          }
        }
      }
    }

    final Timestamps timestamps = context.getProjectDescriptor().timestamps.getStorage();
    for (RealModuleBuildTarget target : dirtyTargets) {
      markDirtyFiles(context, target, timestamps, true, target.isTests() ? DirtyMarkScope.TESTS : DirtyMarkScope.BOTH, null);
    }

    if (context.isMake()) {
      // mark as non-incremental only the module that triggered non-incremental change
      for (RealModuleBuildTarget target : targets) {
        context.markNonIncremental(target);
      }
    }
  }

  private static Set<JpsModule> getDependentModulesRecursively(final JpsModule module, final JpsJavaClasspathKind kind) {
    return JpsJavaExtensionService.dependencies(module).includedIn(kind).recursively().exportedOnly().getModules();
  }

  public static void processFilesToRecompile(CompileContext context, ModuleChunk chunk, FileProcessor processor) throws IOException {
    //noinspection unchecked
    processFilesToRecompile(context, chunk, Condition.TRUE, processor);
  }

  public static void processFilesToRecompile(final CompileContext context,
                                             final ModuleChunk chunk,
                                             final JpsModuleType moduleType,
                                             final FileProcessor processor) throws IOException {
    final Condition<JpsModule> moduleFilter = new Condition<JpsModule>() {
      public boolean value(final JpsModule module) {
        return module.getModuleType() == moduleType;
      }
    };

    processFilesToRecompile(context, chunk, moduleFilter, processor);
  }

  public static void processFilesToRecompile(final CompileContext context,
                                             final ModuleChunk chunk,
                                             final Condition<JpsModule> moduleFilter,
                                             final FileProcessor processor) throws IOException {
    final BuildFSState fsState = context.getProjectDescriptor().fsState;
    for (RealModuleBuildTarget target : chunk.getTargets()) {
      if (moduleFilter.value(target.getModule())) {
        fsState.processFilesToRecompile(context, target, processor);
      }
    }
  }

  static void markDirtyFiles(CompileContext context,
                             RealModuleBuildTarget target,
                             final Timestamps tsStorage,
                             final boolean forceMarkDirty,
                             @NotNull final DirtyMarkScope scope,
                             @Nullable final Set<File> currentFiles) throws IOException {
    final ModuleRootsIndex rootsIndex = context.getProjectDescriptor().rootsIndex;
    final Set<File> excludes = new HashSet<File>(rootsIndex.getModuleExcludes(target.getModule()));
    final Collection<RootDescriptor> roots = new ArrayList<RootDescriptor>();
    for (RootDescriptor rd : rootsIndex.getModuleRoots(context, target.getModuleName())) {
      roots.add(rd);
    }
    for (RootDescriptor rd : roots) {
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
      context.getProjectDescriptor().fsState.clearRecompile(rd);
      traverseRecursively(context, rd, rd.root, excludes, tsStorage, forceMarkDirty, currentFiles);
    }
  }

  private static void traverseRecursively(CompileContext context, final RootDescriptor rd, final File file, Set<File> excludes, @NotNull final Timestamps tsStorage, final boolean forceDirty, @Nullable Set<File> currentFiles) throws IOException {
    final File[] children = file.listFiles();
    if (children != null) { // is directory
      if (children.length > 0 && !JpsPathUtil.isUnder(excludes, file)) {
        for (File child : children) {
          traverseRecursively(context, rd, child, excludes, tsStorage, forceDirty, currentFiles);
        }
      }
    }
    else { // is file
      boolean markDirty = forceDirty;
      if (!markDirty) {
        markDirty = tsStorage.getStamp(file) != FileSystemUtil.lastModified(file);
      }
      if (markDirty) {
        // if it is full project rebuild, all storages are already completely cleared;
        // so passing null because there is no need to access the storage to clear non-existing data
        final Timestamps marker = context.isProjectRebuild() ? null : tsStorage;
        context.getProjectDescriptor().fsState.markDirty(context, file, rd, marker);
      }
      if (currentFiles != null) {
        currentFiles.add(file);
      }
    }
  }

  public enum DirtyMarkScope{
    PRODUCTION, TESTS, BOTH
  }
}
