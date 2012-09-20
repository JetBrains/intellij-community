package org.jetbrains.jps.incremental.fs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.incremental.BuilderRegistry;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ModuleRootsIndex;
import org.jetbrains.jps.incremental.artifacts.ArtifactRootsIndex;
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
  public static final int VERSION = 3;
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.fs.FSState");
  private final Map<BuildTarget, FilesDelta> myDeltas = Collections.synchronizedMap(new HashMap<BuildTarget, FilesDelta>());
  protected final Set<BuildTarget> myInitialScanPerformed = Collections.synchronizedSet(new HashSet<BuildTarget>());

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
  }

  public void load(DataInputStream in, ModuleRootsIndex rootsIndex, ArtifactRootsIndex artifactRootsIndex) throws IOException {
    BuilderRegistry registry = BuilderRegistry.getInstance();
    int typeCount = in.readInt();
    while (typeCount-- > 0) {
      final String typeId = IOUtil.readString(in);
      int targetCount = in.readInt();
      while (targetCount-- > 0) {
        final String id = IOUtil.readString(in);
        BuildTargetType type = registry.getTargetType(typeId);
        boolean loaded = false;
        if (type != null) {
          BuildTarget target = type.createTarget(id, rootsIndex, artifactRootsIndex);
          if (target != null) {
            getDelta(target).load(in, target, rootsIndex, artifactRootsIndex);
            myInitialScanPerformed.add(target);
            loaded = true;
          }
        }
        if (!loaded) {
          LOG.info("Skipping unknown target (typeId=" + typeId + ", type=" + type + ", id=" + id + ")");
          FilesDelta.skip(in);
        }
      }
    }
  }

  public void clearAll() {
    myDeltas.clear();
  }

  public final void clearRecompile(final BuildRootDescriptor rd) {
    getDelta(rd.getTarget()).clearRecompile(rd);
  }

  public boolean markDirty(@Nullable CompileContext context, final File file, final BuildRootDescriptor rd, final @Nullable Timestamps tsStorage) throws IOException {
    final boolean marked = getDelta(rd.getTarget()).markRecompile(rd, file);
    if (marked && tsStorage != null) {
      tsStorage.removeStamp(file, rd.getTarget());
    }
    return marked;
  }

  public boolean markDirtyIfNotDeleted(@Nullable CompileContext context,
                                       final File file,
                                       final RootDescriptor rd,
                                       final @Nullable Timestamps tsStorage) throws IOException {
    final boolean marked = getDelta(rd.target).markRecompileIfNotDeleted(rd, file);
    if (marked && tsStorage != null) {
      tsStorage.removeStamp(file, rd.target);
    }
    return marked;
  }

  public void registerDeleted(BuildTarget target, final File file, @Nullable Timestamps tsStorage) throws IOException {
    registerDeleted(target, file);
    if (tsStorage != null) {
      tsStorage.removeStamp(file, target);
    }
  }

  public void registerDeleted(BuildTarget target, File file) {
    getDelta(target).addDeleted(file);
  }

  public Map<BuildRootDescriptor, Set<File>> getSourcesToRecompile(@NotNull CompileContext context, BuildTarget target) {
    return getDelta(target).getSourcesToRecompile();
  }

  public void clearDeletedPaths(BuildTarget target) {
    final FilesDelta delta = myDeltas.get(target);
    if (delta != null) {
      delta.clearDeletedPaths();
    }
  }

  public Collection<String> getAndClearDeletedPaths(BuildTarget target) {
    final FilesDelta delta = myDeltas.get(target);
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

  public boolean hasWorkToDo(BuildTarget target) {
    if (!myInitialScanPerformed.contains(target)) return true;
    FilesDelta delta = myDeltas.get(target);
    return delta != null && delta.hasChanges();
  }

  public boolean markInitialScanPerformed(BuildTarget target) {
    return myInitialScanPerformed.add(target);
  }
}
