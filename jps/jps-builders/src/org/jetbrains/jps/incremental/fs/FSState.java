package org.jetbrains.jps.incremental.fs;

import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
  private final Map<String, FilesDelta> myDeltas = Collections.synchronizedMap(new HashMap<String, FilesDelta>());
  protected final Set<String> myInitialTestsScanPerformed = Collections.synchronizedSet(new HashSet<String>());
  protected final Set<String> myInitialProductionScanPerformed = Collections.synchronizedSet(new HashSet<String>());

  public void save(DataOutput out) throws IOException {
    out.writeInt(myInitialTestsScanPerformed.size());
    for (String moduleName : myInitialTestsScanPerformed) {
      IOUtil.writeString(moduleName, out);
      getDelta(moduleName).save(out, true);
    }

    out.writeInt(myInitialProductionScanPerformed.size());
    for (String moduleName : myInitialProductionScanPerformed) {
      IOUtil.writeString(moduleName, out);
      getDelta(moduleName).save(out, false);
    }
  }

  public void load(DataInput in) throws IOException {
    int testsDeltaCount = in.readInt();
    while (testsDeltaCount-- > 0) {
      final String moduleName = IOUtil.readString(in);
      getDelta(moduleName).load(in, true);
      myInitialTestsScanPerformed.add(moduleName);
    }

    int productionDeltaCount = in.readInt();
    while (productionDeltaCount-- > 0) {
      final String moduleName = IOUtil.readString(in);
      getDelta(moduleName).load(in, false);
      myInitialProductionScanPerformed.add(moduleName);
    }
  }

  public void clearAll() {
    myDeltas.clear();
  }

  public Set<String> getInitializedModules() {
    final HashSet<String> result = new HashSet<String>(myInitialProductionScanPerformed);
    result.retainAll(myInitialTestsScanPerformed);
    return result;
  }

  public void init(String moduleName, final Collection<String> deleteProduction, final Collection<String> deletedTests, final Map<File, Set<File>> recompileProduction, final Map<File, Set<File>> recompileTests) {
    getDelta(moduleName).init(deleteProduction, deletedTests, recompileProduction, recompileTests);
    myInitialTestsScanPerformed.add(moduleName);
    myInitialProductionScanPerformed.add(moduleName);
  }

  public final void clearRecompile(final RootDescriptor rd) {
    getDelta(rd.module).clearRecompile(rd.root, rd.isTestRoot);
  }

  public boolean markDirty(final File file, final RootDescriptor rd, final @Nullable Timestamps marker) throws IOException {
    final FilesDelta mainDelta = getDelta(rd.module);
    final boolean marked = mainDelta.markRecompile(rd.root, rd.isTestRoot, file);
    if (marked && marker != null) {
      marker.removeStamp(file);
    }
    return marked;
  }

  public boolean markDirtyIfNotDeleted(final File file, final RootDescriptor rd, final @Nullable Timestamps marker) throws IOException {
    final boolean marked = getDelta(rd.module).markRecompileIfNotDeleted(rd.root, rd.isTestRoot, file);
    if (marked && marker != null) {
      marker.removeStamp(file);
    }
    return marked;
  }

  public void registerDeleted(final String moduleName, final File file, final boolean forTests, @Nullable Timestamps tsStorage) throws IOException {
    getDelta(moduleName).addDeleted(file, forTests);
    if (tsStorage != null) {
      tsStorage.removeStamp(file);
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

  public boolean isInitialized(String moduleName) {
    return myInitialTestsScanPerformed.contains(moduleName) && myInitialProductionScanPerformed.contains(moduleName);
  }

  public boolean markInitialScanPerformed(final String moduleName, boolean forTests) {
    return (forTests ? myInitialTestsScanPerformed : myInitialProductionScanPerformed).add(moduleName);
  }
}
