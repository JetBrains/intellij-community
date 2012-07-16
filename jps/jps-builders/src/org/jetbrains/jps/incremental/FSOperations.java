package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.*;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.fs.BuildFSState;
import org.jetbrains.jps.incremental.fs.RootDescriptor;
import org.jetbrains.jps.incremental.storage.Timestamps;

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
    final Set<Module> modules = chunk.getModules();
    for (Module module : modules) {
      markDirtyFiles(context, module, pd.timestamps.getStorage(), true, context.isCompilingTests() ? DirtyMarkScope.TESTS : DirtyMarkScope.PRODUCTION, null);
    }
  }

  public static void markDirtyRecursively(CompileContext context, ModuleChunk chunk) throws IOException {
    final Set<Module> modules = chunk.getModules();
    final Set<Module> dirtyModules = new HashSet<Module>(modules);

    // now mark all modules that depend on dirty modules
    final ClasspathKind classpathKind = ClasspathKind.compile(context.isCompilingTests());
    final ProjectChunks chunks = context.isCompilingTests()? context.getTestChunks() : context.getProductionChunks();
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

    final Timestamps timestamps = context.getProjectDescriptor().timestamps.getStorage();
    for (Module module : dirtyModules) {
      markDirtyFiles(context, module, timestamps, true, context.isCompilingTests() ? DirtyMarkScope.TESTS : DirtyMarkScope.BOTH, null);
    }

    if (context.isMake()) {
      // mark as non-incremental only the module that triggered non-incremental change
      for (Module module : modules) {
        context.markNonIncremental(module);
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

  public static void processFilesToRecompile(CompileContext context, ModuleChunk chunk, FileProcessor processor) throws IOException {
    final BuildFSState fsState = context.getProjectDescriptor().fsState;
    for (Module module : chunk.getModules()) {
      fsState.processFilesToRecompile(context, module, processor);
    }
  }

  static void markDirtyFiles(CompileContext context,
                             Module module,
                             final Timestamps tsStorage,
                             final boolean forceMarkDirty,
                             @NotNull final DirtyMarkScope scope,
                             @Nullable final Set<File> currentFiles) throws IOException {
    final Set<File> excludes = new HashSet<File>();
    for (String excludePath : module.getExcludes()) {
      excludes.add(new File(excludePath));
    }
    final Collection<RootDescriptor> roots = new ArrayList<RootDescriptor>();
    for (RootDescriptor rd : context.getProjectDescriptor().rootsIndex.getModuleRoots(context, module.getName())) {
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
      if (children.length > 0 && !PathUtil.isUnder(excludes, file)) {
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
