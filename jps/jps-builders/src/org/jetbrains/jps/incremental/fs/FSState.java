package org.jetbrains.jps.incremental.fs;

import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.artifacts.ArtifactFilesDelta;
import org.jetbrains.jps.incremental.artifacts.ArtifactSourceTimestampStorage;
import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactRootDescriptor;
import org.jetbrains.jps.incremental.storage.Timestamps;

import java.io.DataInputStream;
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
  private final Map<String, ArtifactFilesDelta> myArtifactDeltas = Collections.synchronizedMap(new HashMap<String, ArtifactFilesDelta>());
  private final Set<String> myArtifactInitialScanPerformed = Collections.synchronizedSet(new HashSet<String>());

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
    out.writeInt(myArtifactInitialScanPerformed.size());
    for (String artifactName : myArtifactInitialScanPerformed) {
      IOUtil.writeString(artifactName, out);
      getDelta(artifactName).save(out);
    }
  }

  public void load(DataInputStream in) throws IOException {
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
    if (in.available() > 0) {//todo[nik] remove this check after version change
      int artifactRootsCount = in.readInt();
      while (artifactRootsCount-- > 0) {
        String name = IOUtil.readString(in);
        getDelta(name).load(in);
        myArtifactInitialScanPerformed.add(name);
      }
    }
  }

  protected ArtifactFilesDelta getDelta(String artifactName) {
    synchronized (myArtifactDeltas) {
      ArtifactFilesDelta delta = myArtifactDeltas.get(artifactName);
      if (delta == null) {
        delta = new ArtifactFilesDelta();
        myArtifactDeltas.put(artifactName, delta);
      }
      return delta;
    }
  }

  public void clearAll() {
    myProductionDeltas.clear();
    myTestDeltas.clear();
    myArtifactDeltas.clear();
  }

  public final void clearRecompile(final RootDescriptor rd) {
    getDelta(rd.module, rd.isTestRoot).clearRecompile(rd.root);
  }

  public void clearRecompile(ArtifactRootDescriptor descriptor) {
    getDelta(descriptor.getArtifactName()).clearRecompile(descriptor.getRootIndex());
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
    final boolean marked = getDelta(rd.module, rd.isTestRoot).markRecompileIfNotDeleted(rd.root, file);
    if (marked && tsStorage != null) {
      tsStorage.removeStamp(file);
    }
    return marked;
  }

  public void markDirty(ArtifactRootDescriptor descriptor, String filePath, ArtifactSourceTimestampStorage timestamps) throws IOException {
    markRecompile(descriptor, filePath);
    timestamps.removeTimestamp(filePath, descriptor.getArtifactId());
  }

  public void markRecompile(ArtifactRootDescriptor descriptor, String filePath) {
    getDelta(descriptor.getArtifactName()).markRecompile(descriptor.getRootIndex(), filePath);
  }

  public void registerDeleted(final String moduleName, final File file, final boolean forTests, @Nullable Timestamps tsStorage) throws IOException {
    getDelta(moduleName, forTests).addDeleted(file);
    if (tsStorage != null) {
      tsStorage.removeStamp(file);
    }
  }

  public void registerDeleted(final String artifactName, final int artifactId, String filePath,
                              ArtifactSourceTimestampStorage timestampStorage) throws IOException {
    registerDeleted(artifactName, filePath);
    timestampStorage.removeTimestamp(filePath, artifactId);
  }

  public void registerDeleted(String artifactName, String filePath) {
    getDelta(artifactName).addDeleted(filePath);
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

  public void clearDeletedPaths(String artifactName) {
    ArtifactFilesDelta delta = myArtifactDeltas.get(artifactName);
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

  public Collection<String> getAndClearDeletedPaths(String artifactName) {
    ArtifactFilesDelta delta = myArtifactDeltas.get(artifactName);
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
    return hasWorkToDoWithModules(false) || hasWorkToDoWithModules(true) || hasWorkToDoWithArtifacts();
  }

  private boolean hasWorkToDoWithArtifacts() {
    for (Map.Entry<String, ArtifactFilesDelta> entry : myArtifactDeltas.entrySet()) {
      if (!myArtifactInitialScanPerformed.contains(entry.getKey()) || entry.getValue().hasChanges()) {
        return true;
      }
    }
    return false;
  }

  private boolean hasWorkToDoWithModules(final boolean forTests) {
    for (Map.Entry<String, FilesDelta> entry : getDeltas(forTests).entrySet()) {
      final String moduleName = entry.getKey();
      if (!getInitialScanPerformedSet(forTests).contains(moduleName)) {
        return true;
      }
      final FilesDelta delta = entry.getValue();
      if (delta.hasChanges()) {
        return true;
      }
    }
    return false;
  }

  public boolean markInitialScanPerformed(final String moduleName, boolean forTests) {
    return getInitialScanPerformedSet(forTests).add(moduleName);
  }

  private Set<String> getInitialScanPerformedSet(boolean forTests) {
    return forTests ? myInitialTestsScanPerformed : myInitialProductionScanPerformed;
  }

  public boolean markInitialScanPerformed(String artifactName) {
    return myArtifactInitialScanPerformed.add(artifactName);
  }
}
