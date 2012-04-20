package org.jetbrains.jps.incremental.fs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.storage.TimestampStorage;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 4/20/12
 */
public class FSState {
  private final Map<String, FilesDelta> myDeltas = Collections.synchronizedMap(new HashMap<String, FilesDelta>());

  public void clearAll() {
    myDeltas.clear();
  }

  public final void clearRecompile(final RootDescriptor rd) {
    getDelta(rd.module).clearRecompile(rd.root, rd.isTestRoot);
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
    final boolean marked = getDelta(rd.module).markRecompileIfNotDeleted(rd.root, rd.isTestRoot, file);
    if (marked && tsStorage != null) {
      tsStorage.markDirty(file);
    }
    return marked;
  }

  public void registerDeleted(final String moduleName, final File file, final boolean forTests, @Nullable TimestampStorage tsStorage) throws IOException {
    getDelta(moduleName).addDeleted(file, forTests);
    if (tsStorage != null) {
      tsStorage.remove(file);
    }
  }

  public Map<File, Set<File>> getSourcesToRecompile(final String moduleName, boolean forTests) {
    return getDelta(moduleName).getSourcesToRecompile(forTests);
  }

  public Collection<String> getDeletedPaths(final String moduleName, final boolean isTest) {
    final FilesDelta delta = myDeltas.get(moduleName);
    if (delta == null) {
      return Collections.emptyList();
    }
    return delta.getDeletedPaths(isTest);
  }

  public void clearDeletedPaths(final String moduleName, final boolean forTests) {
    final FilesDelta delta = myDeltas.get(moduleName);
    if (delta != null) {
      delta.clearDeletedPaths(forTests);
    }
  }

  @NotNull
  protected final FilesDelta getDelta(final String moduleName) {
    synchronized (myDeltas) {
      FilesDelta delta = myDeltas.get(moduleName);
      if (delta == null) {
        delta = new FilesDelta();
        myDeltas.put(moduleName, delta);
      }
      return delta;
    }
  }
}
