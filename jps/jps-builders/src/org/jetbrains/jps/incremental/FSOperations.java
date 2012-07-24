package org.jetbrains.jps.incremental;

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
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleDependency;
import org.jetbrains.jps.model.module.JpsModuleSourceDependency;

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
      pd.fsState.registerDeleted(rd.module, file, rd.isTestRoot, pd.timestamps.getStorage());
    }
  }

  public static void markDirty(CompileContext context, final ModuleChunk chunk) throws IOException {
    final ProjectDescriptor pd = context.getProjectDescriptor();
    pd.fsState.clearContextRoundData(context);
    final Set<JpsModule> modules = chunk.getModules();
    for (JpsModule module : modules) {
      markDirtyFiles(context, module, pd.timestamps.getStorage(), true, context.isCompilingTests() ? DirtyMarkScope.TESTS : DirtyMarkScope.PRODUCTION, null);
    }
  }

  public static void markDirtyRecursively(CompileContext context, ModuleChunk chunk) throws IOException {
    final Set<JpsModule> modules = chunk.getModules();
    final Set<JpsModule> dirtyModules = new HashSet<JpsModule>(modules);

    // now mark all modules that depend on dirty modules
    final JpsJavaClasspathKind classpathKind = JpsJavaClasspathKind.compile(context.isCompilingTests());
    final ProjectChunks chunks = context.isCompilingTests()? context.getTestChunks() : context.getProductionChunks();
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
            dirtyModules.addAll(moduleChunk.getModules());
            break;
          }
        }
      }
    }

    final Timestamps timestamps = context.getProjectDescriptor().timestamps.getStorage();
    for (JpsModule module : dirtyModules) {
      markDirtyFiles(context, module, timestamps, true, context.isCompilingTests() ? DirtyMarkScope.TESTS : DirtyMarkScope.BOTH, null);
    }

    if (context.isMake()) {
      // mark as non-incremental only the module that triggered non-incremental change
      for (JpsModule module : modules) {
        context.markNonIncremental(module);
      }
    }
  }

  private static Set<JpsModule> getDependentModulesRecursively(final JpsModule module, final JpsJavaClasspathKind kind) {
    final Set<JpsModule> result = new HashSet<JpsModule>();

    new Object() {
      final Set<JpsModule> processed = new HashSet<JpsModule>();

      void traverse(JpsModule module, JpsJavaClasspathKind kind, Collection<JpsModule> result, boolean exportedOnly) {
        if (processed.add(module)) {
          for (JpsDependencyElement item : JpsJavaExtensionService.getInstance().getDependencies(module, kind, exportedOnly)) {
            if (item instanceof JpsModuleSourceDependency) {
              result.add(module);
            }
            else if (item instanceof JpsModuleDependency) {
              traverse(((JpsModuleDependency)item).getModule(), kind, result, true);
            }
          }
        }
      }

    }.traverse(module, kind, result, false);

    return result;
  }

  public static void processFilesToRecompile(CompileContext context, ModuleChunk chunk, FileProcessor processor) throws IOException {
    final BuildFSState fsState = context.getProjectDescriptor().fsState;
    for (JpsModule module : chunk.getModules()) {
      fsState.processFilesToRecompile(context, module, processor);
    }
  }

  static void markDirtyFiles(CompileContext context,
                             JpsModule module,
                             final Timestamps tsStorage,
                             final boolean forceMarkDirty,
                             @NotNull final DirtyMarkScope scope,
                             @Nullable final Set<File> currentFiles) throws IOException {
    final ModuleRootsIndex rootsIndex = context.getProjectDescriptor().rootsIndex;
    final Set<File> excludes = new HashSet<File>(rootsIndex.getModuleExcludes(module));
    final Collection<RootDescriptor> roots = new ArrayList<RootDescriptor>();
    for (RootDescriptor rd : rootsIndex.getModuleRoots(context, module.getName())) {
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
        markDirty = tsStorage.getStamp(file) != file.lastModified();
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
