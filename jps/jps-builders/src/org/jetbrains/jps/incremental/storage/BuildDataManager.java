package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.impl.BuildTargetChunk;
import org.jetbrains.jps.builders.impl.storage.BuildTargetStorages;
import org.jetbrains.jps.builders.java.dependencyView.Mappings;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.builders.storage.SourceToOutputMapping;
import org.jetbrains.jps.builders.storage.StorageProvider;

import java.io.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/7/11
 */
public class BuildDataManager implements StorageOwner {
  private static final int VERSION = 18;
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.storage.BuildDataManager");
  private static final String SRC_TO_FORM_STORAGE = "src-form";
  private static final String MAPPINGS_STORAGE = "mappings";

  private final Object mySourceToOutputLock = new Object();
  private final Map<BuildTarget<?>, SourceToOutputMappingImpl> mySourceToOutputs = new HashMap<BuildTarget<?>, SourceToOutputMappingImpl>();
  private final Object myTargetStoragesLock = new Object();
  private final Map<BuildTarget<?>, BuildTargetStorages> myTargetStorages = new HashMap<BuildTarget<?>, BuildTargetStorages>();
  private StorageOwner myTargetStoragesOwner = new CompositeStorageOwner() {
    @Override
    protected Iterable<? extends StorageOwner> getChildStorages() {
      return myTargetStorages.values();
    }
  };

  private final OneToManyPathsMapping mySrcToFormMap;
  private final Mappings myMappings;
  private final BuildDataPaths myDataPaths;
  private final BuildTargetsState myTargetsState;
  private final File myVersionFile;

  public BuildDataManager(final BuildDataPaths dataPaths, BuildTargetsState targetsState, final boolean useMemoryTempCaches) throws IOException {
    myDataPaths = dataPaths;
    myTargetsState = targetsState;
    mySrcToFormMap = new OneToManyPathsMapping(new File(getSourceToFormsRoot(), "data"));
    myMappings = new Mappings(getMappingsRoot(), useMemoryTempCaches);
    myVersionFile = new File(myDataPaths.getDataStorageRoot(), "version.dat");
  }

  public SourceToOutputMapping getSourceToOutputMap(final BuildTarget<?> target) throws IOException {
    SourceToOutputMappingImpl mapping;
    synchronized (mySourceToOutputLock) {
      mapping = mySourceToOutputs.get(target);
      if (mapping == null) {
        mapping = new SourceToOutputMappingImpl(new File(getSourceToOutputMapRoot(target), "data"));
        mySourceToOutputs.put(target, mapping);
      }
    }
    return mapping;
  }

  private File getSourceToOutputMapRoot(BuildTarget<?> target) {
    return new File(myDataPaths.getTargetDataRoot(target), "src-out");
  }

  @NotNull
  public <S extends StorageOwner> S getStorage(@NotNull BuildTarget<?> target, @NotNull StorageProvider<S> provider) throws IOException {
    synchronized (myTargetStoragesLock) {
      BuildTargetStorages storages = myTargetStorages.get(target);
      if (storages == null) {
        storages = new BuildTargetStorages(target, myDataPaths);
        myTargetStorages.put(target, storages);
      }
      return storages.getOrCreateStorage(provider);
    }
  }

  public OneToManyPathsMapping getSourceToFormMap() {
    return mySrcToFormMap;
  }

  public Mappings getMappings() {
    return myMappings;
  }

  public void cleanTargetStorages(BuildTarget<?> target) throws IOException {
    try {
      synchronized (myTargetStoragesLock) {
        BuildTargetStorages storages = myTargetStorages.remove(target);
        if (storages != null) {
          storages.close();
        }
      }
    }
    finally {
      // delete all data except src-out mapping which is cleaned in a special way
      final File[] targetData = myDataPaths.getTargetDataRoot(target).listFiles();
      if (targetData != null) {
        final File srcOutputMapRoot = getSourceToOutputMapRoot(target);
        for (File dataFile : targetData) {
          if (!FileUtil.filesEqual(dataFile, srcOutputMapRoot)) {
            FileUtil.delete(dataFile);
          }
        }
      }
    }
  }

  public void clean() throws IOException {
    try {
      synchronized (myTargetStoragesLock) {
        myTargetStoragesOwner.close();
        myTargetStorages.clear();
      }
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
      myTargetsState.clean();
    }
    saveVersion();
  }

  public void flush(boolean memoryCachesOnly) {
    synchronized (myTargetStoragesLock) {
      myTargetStoragesOwner.flush(memoryCachesOnly);
    }
    synchronized (mySourceToOutputLock) {
      for (SourceToOutputMappingImpl mapping : mySourceToOutputs.values()) {
        mapping.flush(memoryCachesOnly);
      }
    }
    mySrcToFormMap.flush(memoryCachesOnly);
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
      synchronized (myTargetStoragesLock) {
        try {
          myTargetStoragesOwner.close();
        }
        finally {
          myTargetStorages.clear();
        }
      }
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

  public void closeSourceToOutputStorages(Collection<BuildTargetChunk> chunks) throws IOException {
    synchronized (mySourceToOutputLock) {
      for (BuildTargetChunk chunk : chunks) {
        for (BuildTarget<?> target : chunk.getTargets()) {
          final SourceToOutputMappingImpl mapping = mySourceToOutputs.remove(target);
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
      for (SourceToOutputMappingImpl mapping : mySourceToOutputs.values()) {
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

  private File getSourceToFormsRoot() {
    return new File(myDataPaths.getDataStorageRoot(), SRC_TO_FORM_STORAGE);
  }

  private File getMappingsRoot() {
    return new File(myDataPaths.getDataStorageRoot(), MAPPINGS_STORAGE);
  }

  public BuildDataPaths getDataPaths() {
    return myDataPaths;
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
