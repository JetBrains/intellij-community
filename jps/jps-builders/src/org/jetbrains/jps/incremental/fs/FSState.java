package org.jetbrains.jps.incremental.fs;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.CompilerExcludes;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.CompileScope;
import org.jetbrains.jps.incremental.FileProcessor;
import org.jetbrains.jps.incremental.RootDescriptor;
import org.jetbrains.jps.incremental.storage.TimestampStorage;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 4/20/12
 */
public class FSState {
  private final Map<Module, FilesDelta> myDeltas = Collections.synchronizedMap(new HashMap<Module, FilesDelta>());

  public final void clearRecompile(RootDescriptor rd) {
    getDelta(rd.module).clearRecompile(rd.root, rd.isTestRoot);
  }

  public void clearAll() {
    myDeltas.clear();
  }

  public boolean markDirty(final File file, final RootDescriptor rd, final @Nullable TimestampStorage tsStorage) throws IOException {
    final FilesDelta mainDelta = getDelta(rd.module);
    final boolean marked = mainDelta.markRecompile(rd.root, rd.isTestRoot, file);
    if (marked && tsStorage != null) {
      tsStorage.markDirty(file);
    }
    return marked;
  }

  public boolean markDirtyIfNotDeleted(final File file, final RootDescriptor rd, final @Nullable TimestampStorage tsStorage) throws IOException {
    final FilesDelta mainDelta = getDelta(rd.module);
    final boolean marked = mainDelta.markRecompileIfNotDeleted(rd.root, rd.isTestRoot, file);
    if (marked && tsStorage != null) {
      tsStorage.markDirty(file);
    }
    return marked;
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
            if (!rd.isGeneratedSources && stamp > compilationStartStamp) {
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
    final Map<File, Set<File>> data = getSourcesToRecompile(module, context.isCompilingTests());
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

  public Map<File, Set<File>> getSourcesToRecompile(Module module, boolean forTests) {
    return getDelta(module).getSourcesToRecompile(forTests);
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
  protected final FilesDelta getDelta(Module module) {
    synchronized (myDeltas) {
      FilesDelta delta = myDeltas.get(module);
      if (delta == null) {
        delta = new FilesDelta();
        myDeltas.put(module, delta);
      }
      return delta;
    }
  }
}
