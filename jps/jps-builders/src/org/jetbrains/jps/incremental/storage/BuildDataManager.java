package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ether.dependencyView.Mappings;
import org.jetbrains.jps.incremental.Paths;
import org.jetbrains.jps.incremental.artifacts.ArtifactsBuildData;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/7/11
 */
public class BuildDataManager {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.storage.BuildDataManager");
  private static final String SRC_TO_OUTPUTS_STORAGE = "src-out";
  private static final String SRC_TO_FORM_STORAGE = "src-form";
  private static final String MAPPINGS_STORAGE = "mappings";
  private final String myProjectName;

  private final Object mySourceToOutputLock = new Object();
  private final Map<String, SourceToOutputMapping> myProductionSourceToOutputs = new HashMap<String, SourceToOutputMapping>();
  private final Map<String, SourceToOutputMapping> myTestSourceToOutputs = new HashMap<String, SourceToOutputMapping>();

  private final SourceToFormMapping mySrcToFormMap;
  private final ArtifactsBuildData myArtifactsBuildData;
  private final Mappings myMappings;

  public BuildDataManager(String projectName, final boolean useMemoryTempCaches) throws IOException {
    myProjectName = projectName;
    mySrcToFormMap = new SourceToFormMapping(new File(getSourceToFormsRoot(), "data"));
    myMappings = new Mappings(getMappingsRoot(), useMemoryTempCaches);
    final File artifactsDataDir = new File(Paths.getDataStorageRoot(projectName), "artifacts");
    myArtifactsBuildData = new ArtifactsBuildData(artifactsDataDir);
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
            closeOutputToSourceStorages();
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
          closeOutputToSourceStorages();
        }
      }
      finally {
        try {
          closeStorage(mySrcToFormMap);
        }
        finally {
          final Mappings mappings = myMappings;
          if (mappings != null) {
            synchronized (mappings) {
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

  private void closeOutputToSourceStorages() throws IOException {
    IOException ex = null;
    try {
      for (Map.Entry<String, SourceToOutputMapping> entry : myProductionSourceToOutputs.entrySet()) {
        try {
          closeStorage(entry.getValue());
        }
        catch (IOException e) {
          if (e != null) {
            ex = e;
          }
        }
      }
      for (Map.Entry<String, SourceToOutputMapping> entry : myTestSourceToOutputs.entrySet()) {
        try {
          closeStorage(entry.getValue());
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
    return new File(Paths.getDataStorageRoot(myProjectName), SRC_TO_FORM_STORAGE);
  }

  public File getSourceToOutputRoot(String moduleName, boolean forTests) {
    return new File(getSourceToOutputsRoot(), (forTests? "tests" : "production") + "/" + moduleName);
  }

  private File getSourceToOutputsRoot() {
    return new File(Paths.getDataStorageRoot(myProjectName), SRC_TO_OUTPUTS_STORAGE);
  }

  public File getMappingsRoot() {
    return new File(Paths.getDataStorageRoot(myProjectName), MAPPINGS_STORAGE);
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
}
