package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.incremental.BuilderRegistry;
import org.jetbrains.jps.incremental.ModuleRootsIndex;
import org.jetbrains.jps.incremental.artifacts.ArtifactRootsIndex;

import java.io.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author nik
 */
public class BuildTargetsState {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.storage.BuildTargetsState");
  private final File myDataStorageRoot;
  private final ModuleRootsIndex myRootsIndex;
  private final ArtifactRootsIndex myArtifactRootsIndex;
  private AtomicInteger myMaxTargetId = new AtomicInteger(0);
  private ConcurrentMap<BuildTargetType, BuildTargetTypeState> myTypeStates = new ConcurrentHashMap<BuildTargetType, BuildTargetTypeState>();

  public BuildTargetsState(File dataStorageRoot, ModuleRootsIndex rootsIndex, ArtifactRootsIndex artifactRootsIndex) {
    myDataStorageRoot = dataStorageRoot;
    myRootsIndex = rootsIndex;
    myArtifactRootsIndex = artifactRootsIndex;
    File targetTypesFile = getTargetTypesFile();
    try {
      DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(targetTypesFile)));
      try {
        myMaxTargetId.set(input.readInt());
      }
      finally {
        input.close();
      }
    }
    catch (IOException e) {
      LOG.debug("Cannot load " + targetTypesFile + ":" + e.getMessage(), e);
      LOG.debug("Loading all target types to calculate max target id");
      for (BuildTargetType type : BuilderRegistry.getInstance().getTargetTypes()) {
        getTypeState(type);
      }
    }
  }

  public File getTargetTypeDataRoot(BuildTargetType targetType) {
    return new File(getTargetsDataRoot(), targetType.getTypeId());
  }

  public File getTargetsDataRoot() {
    return new File(myDataStorageRoot, "targets");
  }

  private File getTargetTypesFile() {
    return new File(getTargetsDataRoot(), "targetTypes.dat");
  }

  public void save() {
    try {
      File targetTypesFile = getTargetTypesFile();
      FileUtil.createParentDirs(targetTypesFile);
      DataOutputStream output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(targetTypesFile)));
      try {
        output.writeInt(myMaxTargetId.get());
      }
      finally {
        output.close();
      }
    }
    catch (IOException e) {
      LOG.info("Cannot save targets info: " + e.getMessage(), e);
    }
    for (BuildTargetTypeState state : myTypeStates.values()) {
      state.save();
    }
  }

  public int getBuildTargetId(@NotNull BuildTarget target) {
    return getTypeState(target.getTargetType()).getTargetId(target);
  }

  public BuildTargetConfiguration getTargetConfiguration(@NotNull BuildTarget target) {
    return getTypeState(target.getTargetType()).getConfiguration(target);
  }

  public ModuleRootsIndex getRootsIndex() {
    return myRootsIndex;
  }

  public ArtifactRootsIndex getArtifactRootsIndex() {
    return myArtifactRootsIndex;
  }

  private BuildTargetTypeState getTypeState(BuildTargetType type) {
    BuildTargetTypeState state = myTypeStates.get(type);
    if (state == null) {
      state = new BuildTargetTypeState(type, this);
      myTypeStates.putIfAbsent(type, state);
      state = myTypeStates.get(type);
    }
    return state;
  }

  public void markUsedId(int id) {
    int current;
    int max;
    do {
      current = myMaxTargetId.get();
      max = Math.max(id, current);
    }
    while (!myMaxTargetId.compareAndSet(current, max));
  }

  public int getFreeId() {
    return myMaxTargetId.incrementAndGet();
  }

  public File getTargetDataRoot(BuildTarget target) {
    return new File(getTargetTypeDataRoot(target.getTargetType()), target.getId());
  }
}
