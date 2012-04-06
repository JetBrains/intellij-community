package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ether.dependencyView.Mappings;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.incremental.artifacts.ArtifactsBuildData;

import java.io.*;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/7/11
 */
public class BuildDataManager implements StorageOwner {
  private static final int VERSION = 1;
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.storage.BuildDataManager");
  private static final String SRC_TO_OUTPUTS_STORAGE = "src-out";
  private static final String SRC_TO_FORM_STORAGE = "src-form";
  private static final String MAPPINGS_STORAGE = "mappings";

  private final Object mySourceToOutputLock = new Object();
  private final Map<String, SourceToOutputMapping> myProductionSourceToOutputs = new HashMap<String, SourceToOutputMapping>();
  private final Map<String, SourceToOutputMapping> myTestSourceToOutputs = new HashMap<String, SourceToOutputMapping>();

  private final SourceToFormMapping mySrcToFormMap;
  private final ArtifactsBuildData myArtifactsBuildData;
  private final Mappings myMappings;
  private final File myDataStorageRoot;
  private final File myVersionFile;

  public BuildDataManager(final File dataStorageRoot, final boolean useMemoryTempCaches) throws IOException {
    myDataStorageRoot = dataStorageRoot;
    mySrcToFormMap = new SourceToFormMapping(new File(getSourceToFormsRoot(), "data"));
    myMappings = new Mappings(getMappingsRoot(), useMemoryTempCaches);
    myArtifactsBuildData = new ArtifactsBuildData(new File(dataStorageRoot, "artifacts"));
    myVersionFile = new File(myDataStorageRoot, "version.dat");
  }

  public SourceToOutputMapping getSourceToOutputMap(String moduleName, boolean testSources) throws IOException {
    final Map<String, SourceToOutputMapping> storageMap = testSources? myTestSourceToOutputs : myProductionSourceToOutputs;
    SourceToOutputMapping mapping;
    synchronized (mySourceToOutputLock) {
      mapping = storageMap.get(moduleName);
      if (mapping == null) {
        mapping = new SourceToOutputMapping(new File(getSourceToOutputRoot(moduleName, testSources), "data"));
        storageMap.put(moduleName, mapping);
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
          try {
            closeSourceToOutputStorages();
          }
          finally {
            FileUtil.delete(getSourceToOutputsRoot());
          }
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
    }
  }

  public void flush(boolean memoryCachesOnly) {
    myArtifactsBuildData.flush(memoryCachesOnly);
    synchronized (mySourceToOutputLock) {
      for (Map.Entry<String, SourceToOutputMapping> entry : myProductionSourceToOutputs.entrySet()) {
        final SourceToOutputMapping mapping = entry.getValue();
        mapping.flush(memoryCachesOnly);
      }
      for (Map.Entry<String, SourceToOutputMapping> entry : myTestSourceToOutputs.entrySet()) {
        final SourceToOutputMapping mapping = entry.getValue();
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

  public void closeSourceToOutputStorages(ModuleChunk chunk, boolean testSources) throws IOException {
    final Map<String, SourceToOutputMapping> storageMap = testSources? myTestSourceToOutputs : myProductionSourceToOutputs;
    synchronized (mySourceToOutputLock) {
      for (Module module : chunk.getModules()) {
        final String moduleName = module.getName().toLowerCase(Locale.US);
        final SourceToOutputMapping mapping = storageMap.remove(moduleName);
        if (mapping != null) {
          mapping.close();
        }
      }
    }
  }

  private void closeSourceToOutputStorages() throws IOException {
    IOException ex = null;
    try {
      for (Map.Entry<String, SourceToOutputMapping> entry : myProductionSourceToOutputs.entrySet()) {
        try {
          entry.getValue().close();
        }
        catch (IOException e) {
          if (e != null) {
            ex = e;
          }
        }
      }
      for (Map.Entry<String, SourceToOutputMapping> entry : myTestSourceToOutputs.entrySet()) {
        try {
          entry.getValue().close();
        }
        catch (IOException e) {
          if (e != null) {
            ex = e;
          }
        }
      }
    }
    finally {
      myProductionSourceToOutputs.clear();
      myTestSourceToOutputs.clear();
    }
    if (ex != null) {
      throw ex;
    }
  }

  public File getSourceToFormsRoot() {
    return new File(myDataStorageRoot, SRC_TO_FORM_STORAGE);
  }

  public File getSourceToOutputRoot(String moduleName, boolean forTests) {
    return new File(getSourceToOutputsRoot(), (forTests? "tests" : "production") + "/" + moduleName);
  }

  private File getSourceToOutputsRoot() {
    return new File(myDataStorageRoot, SRC_TO_OUTPUTS_STORAGE);
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
