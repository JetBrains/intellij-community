package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ether.dependencyView.Mappings;
import org.jetbrains.jps.incremental.Paths;
import org.jetbrains.jps.incremental.ProjectBuildException;

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
  private final Mappings myMappings;

  public BuildDataManager(String projectName, final boolean useMemoryTempCaches) throws ProjectBuildException {
    myProjectName = projectName;
    try {
      mySrcToFormMap = createStorage(getSourceToFormsRoot(), new StorageFactory<SourceToFormMapping>() {
        public SourceToFormMapping create(File dataFile) throws Exception {
          return new SourceToFormMapping(dataFile);
        }
      });

      final File mappingsRoot = getMappingsRoot();
      myMappings = createStorage(mappingsRoot, new StorageFactory<Mappings>() {
        public Mappings create(File dataFile) throws Exception {
          return new Mappings(mappingsRoot, useMemoryTempCaches);
        }
      });
    }
    catch (Exception e) {
      try {
        clean();
      }
      catch (IOException ignored) {
        LOG.info(ignored);
      }
      throw new ProjectBuildException(e);
    }
  }

  public SourceToOutputMapping getSourceToOutputMap(String moduleName, boolean testSources) throws Exception {
    final Map<String, SourceToOutputMapping> storageMap = testSources? myTestSourceToOutputs : myProductionSourceToOutputs;
    SourceToOutputMapping mapping;
    synchronized (mySourceToOutputLock) {
      mapping = storageMap.get(moduleName);
      if (mapping == null) {
        mapping = createStorage(getSourceToOutputRoot(moduleName, testSources), new StorageFactory<SourceToOutputMapping>() {
          public SourceToOutputMapping create(File dataFile) throws Exception {
            return new SourceToOutputMapping(dataFile);
          }
        });
        storageMap.put(moduleName, mapping);
      }
    }
    return mapping;
  }

  public SourceToFormMapping getSourceToFormMap() {
    return mySrcToFormMap;
  }

  public Mappings getMappings() {
    return myMappings;
  }

  public void clean() throws IOException {
    try {
      synchronized (mySourceToOutputLock) {
        closeOutputToSourceStorages();
        FileUtil.delete(getSourceToOutputsRoot());
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

  public void close() {
    try {
      synchronized (mySourceToOutputLock) {
        closeOutputToSourceStorages();
      }
    }
    finally {
      try {
        closeStorage(getSourceToFormsRoot(), mySrcToFormMap);
      }
      finally {
        final Mappings mappings = myMappings;
        if (mappings != null) {
          synchronized (mappings) {
            mappings.close();
          }
        }
      }
    }
  }

  private void closeOutputToSourceStorages() {
    for (Map.Entry<String, SourceToOutputMapping> entry : myProductionSourceToOutputs.entrySet()) {
      closeStorage(getSourceToOutputRoot(entry.getKey(), false), entry.getValue());
    }
    for (Map.Entry<String, SourceToOutputMapping> entry : myTestSourceToOutputs.entrySet()) {
      closeStorage(getSourceToOutputRoot(entry.getKey(), true), entry.getValue());
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

  private interface StorageFactory<T> {
    T create(File dataFile) throws Exception;
  }

  private static <T> T createStorage(File root, StorageFactory<T> factory) throws Exception {
    final File dataFile = new File(root, "data");
    try {
      return factory.create(dataFile);
    }
    catch (Exception e) {
      FileUtil.delete(root);
      return factory.create(dataFile);
    }
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

  private static void closeStorage(File root, @Nullable AbstractStateStorage<?, ?> storage) {
    if (storage != null) {
      synchronized (storage) {
        try {
          storage.close();
        }
        catch (IOException e) {
          LOG.error(e);
          FileUtil.delete(root);
        }
      }
    }
  }
}
