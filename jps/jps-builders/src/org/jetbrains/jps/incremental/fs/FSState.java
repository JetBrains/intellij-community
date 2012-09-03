package org.jetbrains.jps.incremental.fs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.BuilderService;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.incremental.artifacts.ArtifactFilesDelta;
import org.jetbrains.jps.incremental.artifacts.ArtifactSourceTimestampStorage;
import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactRootDescriptor;
import org.jetbrains.jps.incremental.storage.Timestamps;
import org.jetbrains.jps.service.JpsServiceManager;

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
  public static final int VERSION = 1;
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.fs.FSState");
  private final Map<BuildTarget, FilesDelta> myDeltas = Collections.synchronizedMap(new HashMap<BuildTarget, FilesDelta>());
  protected final Set<BuildTarget> myInitialScanPerformed = Collections.synchronizedSet(new HashSet<BuildTarget>());
  private final Map<String, ArtifactFilesDelta> myArtifactDeltas = Collections.synchronizedMap(new HashMap<String, ArtifactFilesDelta>());
  private final Set<String> myArtifactInitialScanPerformed = Collections.synchronizedSet(new HashSet<String>());

  public void save(DataOutput out) throws IOException {
    MultiMap<BuildTargetType, BuildTarget> targetsByType = new MultiMap<BuildTargetType, BuildTarget>();
    for (BuildTarget target : myInitialScanPerformed) {
      targetsByType.putValue(target.getTargetType(), target);
    }
    out.writeInt(targetsByType.size());
    for (BuildTargetType type : targetsByType.keySet()) {
      IOUtil.writeString(type.getTypeId(), out);
      Collection<BuildTarget> targets = targetsByType.get(type);
      out.writeInt(targets.size());
      for (BuildTarget target : targets) {
        IOUtil.writeString(target.getId(), out);
        getDelta(target).save(out);
      }
    }
    out.writeInt(myArtifactInitialScanPerformed.size());
    for (String artifactName : myArtifactInitialScanPerformed) {
      IOUtil.writeString(artifactName, out);
      getDelta(artifactName).save(out);
    }
  }

  public void load(DataInputStream in, ProjectDescriptor projectDescriptor) throws IOException {
    Map<String, BuildTargetType> types = new HashMap<String, BuildTargetType>();
    for (BuilderService service : JpsServiceManager.getInstance().getExtensions(BuilderService.class)) {
      for (BuildTargetType type : service.getTargetTypes()) {
        String id = type.getTypeId();
        BuildTargetType old = types.put(id, type);
        if (old != null) {
          LOG.error("Two build target types (" + type + ", " + old + ") use same id (" + id + ")");
        }
      }
    }
    int typeCount = in.readInt();
    while (typeCount-- > 0) {
      final String typeId = IOUtil.readString(in);
      int targetCount = in.readInt();
      while (targetCount-- > 0) {
        final String id = IOUtil.readString(in);
        BuildTargetType type = types.get(typeId);
        boolean loaded = false;
        if (type != null) {
          BuildTarget target = type.createTarget(id, projectDescriptor);
          if (target != null) {
            getDelta(target).load(in);
            myInitialScanPerformed.add(target);
            loaded = true;
          }
        }
        if (!loaded) {
          LOG.info("Skipping unknown target (typeId=" + typeId + ", type=" + type + ", id=" + id + ")");
          new FilesDelta().load(in);
        }
      }
    }

    int artifactRootsCount = in.readInt();
    while (artifactRootsCount-- > 0) {
      String name = IOUtil.readString(in);
      getDelta(name).load(in);
      myArtifactInitialScanPerformed.add(name);
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
    myDeltas.clear();
    myArtifactDeltas.clear();
  }

  public final void clearRecompile(final RootDescriptor rd) {
    getDelta(rd.target).clearRecompile(rd.root);
  }

  public void clearRecompile(ArtifactRootDescriptor descriptor) {
    getDelta(descriptor.getArtifactName()).clearRecompile(descriptor.getRootIndex());
  }

  public boolean markDirty(@Nullable CompileContext context, final File file, final RootDescriptor rd, final @Nullable Timestamps tsStorage) throws IOException {
    final FilesDelta mainDelta = getDelta(rd.target);
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
    final boolean marked = getDelta(rd.target).markRecompileIfNotDeleted(rd.root, file);
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

  public void registerDeleted(ModuleBuildTarget target, final File file, @Nullable Timestamps tsStorage) throws IOException {
    getDelta(target).addDeleted(file);
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

  public Map<File, Set<File>> getSourcesToRecompile(@NotNull CompileContext context, ModuleBuildTarget target) {
    return getDelta(target).getSourcesToRecompile();
  }

  public void clearDeletedPaths(ModuleBuildTarget target) {
    final FilesDelta delta = myDeltas.get(target);
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

  public Collection<String> getAndClearDeletedPaths(ModuleBuildTarget target) {
    final FilesDelta delta = myDeltas.get(target);
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
  protected final FilesDelta getDelta(BuildTarget buildTarget) {
    synchronized (myDeltas) {
      FilesDelta delta = myDeltas.get(buildTarget);
      if (delta == null) {
        delta = new FilesDelta();
        myDeltas.put(buildTarget, delta);
      }
      return delta;
    }
  }

  public boolean hasWorkToDo() {
    return hasWorkToDoWithModules() || hasWorkToDoWithArtifacts();
  }

  private boolean hasWorkToDoWithArtifacts() {
    for (Map.Entry<String, ArtifactFilesDelta> entry : myArtifactDeltas.entrySet()) {
      if (!myArtifactInitialScanPerformed.contains(entry.getKey()) || entry.getValue().hasChanges()) {
        return true;
      }
    }
    return false;
  }

  private boolean hasWorkToDoWithModules() {
    for (Map.Entry<BuildTarget, FilesDelta> entry : myDeltas.entrySet()) {
      if (!myInitialScanPerformed.contains(entry.getKey()) || entry.getValue().hasChanges()) {
        return true;
      }
    }
    return false;
  }

  public boolean markInitialScanPerformed(ModuleBuildTarget target) {
    return myInitialScanPerformed.add(target);
  }

  public boolean markInitialScanPerformed(String artifactName) {
    return myArtifactInitialScanPerformed.add(artifactName);
  }

  public boolean isInitialScanPerformed(ModuleBuildTarget target) {
    return myInitialScanPerformed.contains(target);
  }

  public boolean isInitialScanPerformed(String artifactName) {
    return myArtifactInitialScanPerformed.contains(artifactName);
  }
}
