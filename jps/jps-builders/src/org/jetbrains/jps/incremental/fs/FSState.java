package org.jetbrains.jps.incremental.fs;

import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.storage.Timestamps;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 4/20/12
 */
public class FSState {
  private final Map<String, FilesDelta> myProductionDeltas = Collections.synchronizedMap(new HashMap<String, FilesDelta>());
  private final Map<String, FilesDelta> myTestDeltas = Collections.synchronizedMap(new HashMap<String, FilesDelta>());
  protected final Set<String> myInitialTestsScanPerformed = Collections.synchronizedSet(new HashSet<String>());
  protected final Set<String> myInitialProductionScanPerformed = Collections.synchronizedSet(new HashSet<String>());

  public void save(DataOutput out) throws IOException {
    out.writeInt(myInitialTestsScanPerformed.size());
    for (String moduleName : myInitialTestsScanPerformed) {
      IOUtil.writeString(moduleName, out);
      getDelta(moduleName, true).save(out);
    }

    out.writeInt(myInitialProductionScanPerformed.size());
    for (String moduleName : myInitialProductionScanPerformed) {
      IOUtil.writeString(moduleName, out);
      getDelta(moduleName, false).save(out);
    }
  }

  public void load(DataInput in) throws IOException {
    int testsDeltaCount = in.readInt();
    while (testsDeltaCount-- > 0) {
      final String moduleName = IOUtil.readString(in);
      getDelta(moduleName, true).load(in);
      myInitialTestsScanPerformed.add(moduleName);
    }

    int productionDeltaCount = in.readInt();
    while (productionDeltaCount-- > 0) {
      final String moduleName = IOUtil.readString(in);
      getDelta(moduleName, false).load(in);
      myInitialProductionScanPerformed.add(moduleName);
    }
  }

  public void clearAll() {
    myProductionDeltas.clear();
    myTestDeltas.clear();
  }

  public void init(String moduleName, final Collection<String> deleteProduction, final Collection<String> deletedTests, final Map<File, Set<File>> recompileProduction, final Map<File, Set<File>> recompileTests) {
    getDelta(moduleName, true).init(deletedTests,  recompileTests);
    getDelta(moduleName, false).init(deleteProduction, recompileProduction);
    myInitialTestsScanPerformed.add(moduleName);
    myInitialProductionScanPerformed.add(moduleName);
  }

  public final void clearRecompile(final RootDescriptor rd) {
    getDelta(rd.module, rd.isTestRoot).clearRecompile(rd.root);
  }

  public boolean markDirty(@Nullable CompileContext context, final File file, final RootDescriptor rd, final @Nullable Timestamps tsStorage) throws IOException {
    final FilesDelta mainDelta = getDelta(rd.module, rd.isTestRoot);
    final boolean marked = mainDelta.markRecompile(rd.root, file);
    if (marked && tsStorage != null) {
      tsStorage.removeStamp(file);
    }
    return marked;
  }

  public boolean markDirtyIfNotDeleted(@Nullable CompileContext context,
                                       final File file,
                                       final RootDescriptor rd,
                                       final @Nullable Timestamps tsStorage) throws IOException {
    final boolean marked = getDelta(rd.module, rd.isTestRoot).markRecompileIfNotDeleted(rd.root, rd.isTestRoot, file);
    if (marked && tsStorage != null) {
      tsStorage.removeStamp(file);
    }
    return marked;
  }

  public void registerDeleted(final String moduleName, final File file, final boolean forTests, @Nullable Timestamps tsStorage) throws IOException {
    getDelta(moduleName, forTests).addDeleted(file);
    if (tsStorage != null) {
      tsStorage.removeStamp(file);
    }
  }

  public Map<File, Set<File>> getSourcesToRecompile(@NotNull CompileContext context, final String moduleName, boolean forTests) {
    return getDelta(moduleName, forTests).getSourcesToRecompile();
  }

  public void clearDeletedPaths(final String moduleName, final boolean forTests) {
    final FilesDelta delta = getDeltas(forTests).get(moduleName);
    if (delta != null) {
      delta.clearDeletedPaths();
    }
  }

  private Map<String, FilesDelta> getDeltas(boolean forTests) {
    return forTests ? myTestDeltas : myProductionDeltas;
  }

  public Collection<String> getAndClearDeletedPaths(final String moduleName, final boolean forTests) {
    final FilesDelta delta = getDeltas(forTests).get(moduleName);
    if (delta != null) {
      return delta.getAndClearDeletedPaths();
    }
    return Collections.emptyList();
  }

  @NotNull
  protected final FilesDelta getDelta(final String moduleName, boolean forTests) {
    Map<String, FilesDelta> deltas = getDeltas(forTests);
    synchronized (deltas) {
      FilesDelta delta = deltas.get(moduleName);
      if (delta == null) {
        delta = new FilesDelta();
        deltas.put(moduleName, delta);
      }
      return delta;
    }
  }

  public boolean hasWorkToDo() {
    return hasWorkToDo(false) || hasWorkToDo(true);
  }

  private boolean hasWorkToDo(final boolean forTests) {
    for (Map.Entry<String, FilesDelta> entry : getDeltas(forTests).entrySet()) {
      final String moduleName = entry.getKey();
      if (!myInitialProductionScanPerformed.contains(moduleName) || !myInitialTestsScanPerformed.contains(moduleName)) {
        return true;
      }
      final FilesDelta delta = entry.getValue();
      if (delta.hasPathsToDelete() || delta.hasSourcesToRecompile()) {
        return true;
      }
    }
    return false;
  }

  public boolean markInitialScanPerformed(final String moduleName, boolean forTests) {
    return (forTests ? myInitialTestsScanPerformed : myInitialProductionScanPerformed).add(moduleName);
  }
}
