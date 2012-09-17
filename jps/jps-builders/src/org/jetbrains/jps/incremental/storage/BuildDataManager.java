package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.java.dependencyView.Mappings;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.incremental.artifacts.ArtifactsBuildData;

import java.io.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/7/11
 */
public class BuildDataManager implements StorageOwner {
  private static final int VERSION = 12;
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.storage.BuildDataManager");
  private static final String SRC_TO_OUTPUTS_STORAGE = "src-out";
  private static final String SRC_TO_FORM_STORAGE = "src-form";
  private static final String MAPPINGS_STORAGE = "mappings";

  private final Object mySourceToOutputLock = new Object();
  private final Map<BuildTarget, SourceToOutputMapping> mySourceToOutputs = new HashMap<BuildTarget, SourceToOutputMapping>();

  private final SourceToFormMapping mySrcToFormMap;
  private final ArtifactsBuildData myArtifactsBuildData;
  private final ModuleOutputRootsLayout myOutputRootsLayout;
  private final Mappings myMappings;
  private final File myDataStorageRoot;
  private final BuildTargetsState myTargetsState;
  private final File myVersionFile;

  public BuildDataManager(final File dataStorageRoot, BuildTargetsState targetsState, final boolean useMemoryTempCaches) throws IOException {
    myDataStorageRoot = dataStorageRoot;
    myTargetsState = targetsState;
    mySrcToFormMap = new SourceToFormMapping(new File(getSourceToFormsRoot(), "data"));
    myOutputRootsLayout = new ModuleOutputRootsLayout(new File(getOutputsLayoutRoot(), "data"));
    myMappings = new Mappings(getMappingsRoot(), useMemoryTempCaches);
    myArtifactsBuildData = new ArtifactsBuildData(new File(dataStorageRoot, "artifacts"));
    myVersionFile = new File(myDataStorageRoot, "version.dat");
  }

  private File getOutputsLayoutRoot() {
    return new File(myDataStorageRoot, "output-roots");
  }

  public SourceToOutputMapping getSourceToOutputMap(final BuildTarget target) throws IOException {
    SourceToOutputMapping mapping;
    synchronized (mySourceToOutputLock) {
      mapping = mySourceToOutputs.get(target);
      if (mapping == null) {
        mapping = new SourceToOutputMapping(new File(myTargetsState.getTargetDataRoot(target), "src-out" + File.separator + "data"));
        mySourceToOutputs.put(target, mapping);
      }
    }
    return mapping;
  }

  public ArtifactsBuildData getArtifactsBuildData() {
    return myArtifactsBuildData;
  }

  public SourceToFormMapping getSourceToFormMap() {
    return mySrcToFormMap;
  }

  public ModuleOutputRootsLayout getOutputRootsLayout() {
    return myOutputRootsLayout;
  }

  public Mappings getMappings() {
    return myMappings;
  }

  public void clean() throws IOException {
    try {
      myArtifactsBuildData.clean();
    }
    finally {
      try {
        synchronized (mySourceToOutputLock) {
          closeSourceToOutputStorages();
        }
      }
      finally {
        try {
          wipeStorage(getSourceToFormsRoot(), mySrcToFormMap);
        }
        finally {
          try {
            wipeStorage(getOutputsLayoutRoot(), myOutputRootsLayout);
          }
          finally {
            final Mappings mappings = myMappings;
            if (mappings != null) {
              synchronized (mappings) {
                mappings.clean();
              }
            }
            else {
              FileUtil.delete(getMappingsRoot());
            }
          }
        }
      }
      myTargetsState.clean();
    }
    saveVersion();
  }

  public void flush(boolean memoryCachesOnly) {
    myArtifactsBuildData.flush(memoryCachesOnly);
    synchronized (mySourceToOutputLock) {
      for (SourceToOutputMapping mapping : mySourceToOutputs.values()) {
        mapping.flush(memoryCachesOnly);
      }
    }
    mySrcToFormMap.flush(memoryCachesOnly);
    myOutputRootsLayout.flush(memoryCachesOnly);
    final Mappings mappings = myMappings;
    if (mappings != null) {
      synchronized (mappings) {
        mappings.flush(memoryCachesOnly);
      }
    }
  }

  public void close() throws IOException {
    try {
      myTargetsState.save();
      myArtifactsBuildData.close();
    }
    finally {
      try {
        synchronized (mySourceToOutputLock) {
          closeSourceToOutputStorages();
        }
      }
      finally {
        try {
          closeStorage(mySrcToFormMap);
        }
        finally {
          try {
            closeStorage(myOutputRootsLayout);
          }
          finally {
            final Mappings mappings = myMappings;
            if (mappings != null) {
              try {
                mappings.close();
              }
              catch (RuntimeException e) {
                final Throwable cause = e.getCause();
                if (cause instanceof IOException) {
                  throw ((IOException)cause);
                }
                throw e;
              }
            }
          }
        }
      }
    }
  }

  public void closeSourceToOutputStorages(Collection<ModuleChunk> chunks) throws IOException {
    synchronized (mySourceToOutputLock) {
      for (ModuleChunk chunk : chunks) {
        for (ModuleBuildTarget target : chunk.getTargets()) {
          final SourceToOutputMapping mapping = mySourceToOutputs.remove(target);
          if (mapping != null) {
            mapping.close();
          }
        }
      }
    }
  }

  private void closeSourceToOutputStorages() throws IOException {
    IOException ex = null;
    try {
      for (SourceToOutputMapping mapping : mySourceToOutputs.values()) {
        try {
          mapping.close();
        }
        catch (IOException e) {
          if (e != null) {
            ex = e;
          }
        }
      }
    }
    finally {
      mySourceToOutputs.clear();
    }
    if (ex != null) {
      throw ex;
    }
  }

  public File getSourceToFormsRoot() {
    return new File(myDataStorageRoot, SRC_TO_FORM_STORAGE);
  }

  public File getMappingsRoot() {
    return new File(myDataStorageRoot, MAPPINGS_STORAGE);
  }

  public File getDataStorageRoot() {
    return myDataStorageRoot;
  }

  private static void wipeStorage(File root, @Nullable AbstractStateStorage<?, ?> storage) {
    if (storage != null) {
      synchronized (storage) {
        storage.wipe();
      }
    }
    else {
      FileUtil.delete(root);
    }
  }

  private static void closeStorage(@Nullable AbstractStateStorage<?, ?> storage) throws IOException {
    if (storage != null) {
      synchronized (storage) {
        storage.close();
      }
    }
  }

  private Boolean myVersionDiffers = null;

  public boolean versionDiffers() {
    final Boolean cached = myVersionDiffers;
    if (cached != null) {
      return cached;
    }
    try {
      final DataInputStream is = new DataInputStream(new FileInputStream(myVersionFile));
      try {
        final boolean diff = is.readInt() != VERSION;
        myVersionDiffers = diff;
        return diff;
      }
      finally {
        is.close();
      }
    }
    catch (FileNotFoundException ignored) {
      return false; // treat it as a new dir
    }
    catch (IOException ex) {
      LOG.info(ex);
    }
    return true;
  }

  public void saveVersion() {
    final Boolean differs = myVersionDiffers;
    if (differs == null || differs) {
      try {
        FileUtil.createIfDoesntExist(myVersionFile);
        final DataOutputStream os = new DataOutputStream(new FileOutputStream(myVersionFile));
        try {
          os.writeInt(VERSION);
          myVersionDiffers = Boolean.FALSE;
        }
        finally {
          os.close();
        }
      }
      catch (IOException ignored) {
      }
    }
  }
}
