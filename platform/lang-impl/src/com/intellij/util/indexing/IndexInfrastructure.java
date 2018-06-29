/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * @author max
 */
package com.intellij.util.indexing;

import com.google.gson.GsonBuilder;
import com.intellij.onair.index.BTreeIndexStorage;
import com.intellij.onair.index.BTreeIntPersistentMap;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.CacheUpdateRunner;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.psi.stubs.StubIndexKey;
import com.intellij.psi.stubs.StubUpdatingIndex;
import com.intellij.util.SystemProperties;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentMap;
import intellij.platform.onair.storage.StorageImpl;
import intellij.platform.onair.storage.api.Address;
import intellij.platform.onair.storage.api.Novelty;
import intellij.platform.onair.storage.api.NoveltyImpl;
import intellij.platform.onair.storage.api.Storage;
import intellij.platform.onair.tree.MockStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.*;

@SuppressWarnings("HardCodedStringLiteral")
public class IndexInfrastructure {
  private static final boolean ourUnitTestMode = ApplicationManager.getApplication().isUnitTestMode();
  private static final String STUB_VERSIONS = ".versions";
  private static final String PERSISTENT_INDEX_DIRECTORY_NAME = ".persistent";
  private static final boolean ourDoParallelIndicesInitialization = SystemProperties
    .getBooleanProperty("idea.parallel.indices.initialization", false);
  public static final boolean ourDoAsyncIndicesInitialization = SystemProperties.getBooleanProperty("idea.async.indices.initialization", true);
  private static final ExecutorService ourGenesisExecutor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor(
    "IndexInfrastructure Pool");

  private IndexInfrastructure() {
  }

  public static Storage storage = new MockStorage();

  public static final Novelty indexNovelty;

  static {
    try {
      indexNovelty = new NoveltyImpl(FileUtil.createTempFile("novelty-", ".here"));
    }
    catch (IOException e) {
      throw new RuntimeException();
    }
  }

  public static ConcurrentHashMap<String, BTreeIndexStorage> indexStorages = new ConcurrentHashMap<>();
  public static ConcurrentHashMap<String, BTreeIntPersistentMap> forwardStorages = new ConcurrentHashMap<>();

  public static Map downloadIndexMetaData(String revision) {
    String bucket = "onair-index-data";
    String region = "eu-central-1";

    try {
      return new GsonBuilder().create().fromJson(new DataInputStream(new URL(
        "https://s3." + region + ".amazonaws.com/" + bucket + "/" + revision + "/meta").openStream()).readUTF(), Map.class);
    }
    catch (Exception e) {
      throw new RuntimeException("exception downloading index data for revision " + revision, e);
    }
  }

  public static Map indexMeta = null;

  static {
    String revision = System.getProperty("onair.revision");

    if (revision != null && !revision.trim().isEmpty()) {
      indexMeta = downloadIndexMetaData(revision);
      try {
        storage = new StorageImpl(new InetSocketAddress("test-index.bvey1z.cfg.euc1.cache.amazonaws.com", 11211));
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public static <K, V> VfsAwareIndexStorage<K, V> createIndexStorage(ID<?, ?> indexId,
                                                                     KeyDescriptor<K> keyDescriptor,
                                                                     DataExternalizer<V> valueExternalizer,
                                                                     int cacheSize) {
    BTreeIndexStorage.AddressPair address;
    int newRevision = 17;
    int baseRevision = -1;
    if (indexMeta != null) {
      Map m = (Map)(((Map)indexMeta.get("inverted-indices")).get(indexId.getName()));
      List invertedAddr = (List)m.get("inverted");
      List internaryAddr = (List)m.get("internary");
      Address internary = internaryAddr != null ? new Address(Long.parseLong((String)internaryAddr.get(1)),
                                                              Long.parseLong((String)internaryAddr.get(0))) : null;
      address = new BTreeIndexStorage.AddressPair(internary, new Address(Long.parseLong((String)invertedAddr.get(1)),
                                                                         Long.parseLong((String)invertedAddr.get(0))));
      baseRevision = Integer.parseInt((String)indexMeta.get("revision-int"));
    } else {
      address = null;
    }
    BTreeIndexStorage<K, V> storage =
      new BTreeIndexStorage<>(keyDescriptor,
                              valueExternalizer,
                              IndexInfrastructure.storage,
                              indexNovelty,
                              address,
                              cacheSize,
                              newRevision,
                              baseRevision);
    indexStorages.put(indexId.getName(), storage);
    return storage;
  }

  public static <V> PersistentMap<Integer, V> createForwardIndexStorage(ID<?, ?> indexId,
                                                                        DataExternalizer<V> valueExternalizer) {

    Address head = null;
    if (indexMeta != null) {
      List addr = (List)((Map)(indexMeta.get("forward-indices"))).get(indexId.getName());

      head = new Address(Long.parseLong((String)addr.get(1)),
                         Long.parseLong((String)addr.get(0)));
    }
    BTreeIntPersistentMap<V> map = new BTreeIntPersistentMap<>(valueExternalizer, storage, indexNovelty, head);
    forwardStorages.put(indexId.getName(), map);
    return map;
  }

  @NotNull
  public static File getVersionFile(@NotNull ID<?, ?> indexName) {
    return new File(getIndexDirectory(indexName, true), indexName + ".ver");
  }

  @NotNull
  public static File getStorageFile(@NotNull ID<?, ?> indexName) {
    return new File(getIndexRootDir(indexName), indexName.getName());
  }

  @NotNull
  public static File getInputIndexStorageFile(@NotNull ID<?, ?> indexName) {
    return new File(getIndexRootDir(indexName), indexName +"_inputs");
  }

  @NotNull
  public static File getIndexRootDir(@NotNull ID<?, ?> indexName) {
    return getIndexDirectory(indexName, false);
  }

  public static File getPersistentIndexRoot() {
    File indexDir = new File(PathManager.getIndexRoot() + File.separator + PERSISTENT_INDEX_DIRECTORY_NAME);
    indexDir.mkdirs();
    return indexDir;
  }

  @NotNull
  public static File getPersistentIndexRootDir(@NotNull ID<?, ?> indexName) {
    return getIndexDirectory(indexName, false, PERSISTENT_INDEX_DIRECTORY_NAME);
  }

  @NotNull
  private static File getIndexDirectory(@NotNull ID<?, ?> indexName, boolean forVersion) {
    return getIndexDirectory(indexName, forVersion, "");
  }

  @NotNull
  private static File getIndexDirectory(@NotNull ID<?, ?> indexName, boolean forVersion, String relativePath) {
    final String dirName = indexName.getName().toLowerCase(Locale.US);
    File indexDir;

    if (indexName instanceof StubIndexKey) {
      // store StubIndices under StubUpdating index' root to ensure they are deleted
      // when StubUpdatingIndex version is changed
      indexDir = new File(getIndexDirectory(StubUpdatingIndex.INDEX_ID, false, relativePath), forVersion ? STUB_VERSIONS : dirName);
    } else {
      if (relativePath.length() > 0) relativePath = File.separator + relativePath;
      indexDir = new File(PathManager.getIndexRoot() + relativePath, dirName);
    }
    indexDir.mkdirs();
    return indexDir;
  }

  @Nullable
  public static VirtualFile findFileById(@NotNull PersistentFS fs, final int id) {
    if (ourUnitTestMode) {
      final VirtualFile testFile = findTestFile(id);
      if (testFile != null) {
        return testFile;
      }
    }

    return fs.findFileById(id);

    /*

    final boolean isDirectory = fs.isDirectory(id);
    final DirectoryInfo directoryInfo = isDirectory ? dirIndex.getInfoForDirectoryId(id) : dirIndex.getInfoForDirectoryId(fs.getParent(id));
    if (directoryInfo != null && (directoryInfo.contentRoot != null || directoryInfo.sourceRoot != null || directoryInfo.libraryClassRoot != null)) {
      return isDirectory? directoryInfo.directory : directoryInfo.directory.findChild(fs.getName(id));
    }
    return null;
    */
  }

  @Nullable
  public static VirtualFile findFileByIdIfCached(@NotNull PersistentFS fs, final int id) {
    if (ourUnitTestMode) {
      final VirtualFile testFile = findTestFile(id);
      if (testFile != null) {
        return testFile;
      }
    }
    return fs.findFileByIdIfCached(id);
  }

  @Nullable
  private static VirtualFile findTestFile(final int id) {
    return DummyFileSystem.getInstance().findById(id);
  }

  public static <T> Future<T> submitGenesisTask(Callable<T> action) {
    return ourGenesisExecutor.submit(action);
  }

  public abstract static class DataInitialization<T> implements Callable<T> {
    private final List<ThrowableRunnable> myNestedInitializationTasks = new ArrayList<>();

    @Override
    public final T call() throws Exception {
      long started = System.nanoTime();
      try {
        prepare();
        runParallelNestedInitializationTasks();
        return finish();
      } finally {
        Logger.getInstance(getClass().getName()).info("Initialization done:" + (System.nanoTime() - started) / 1000000);
      }
    }

    protected T finish() {
      return null;
    }

    protected void prepare() {}
    protected abstract void onThrowable(Throwable t);

    public void addNestedInitializationTask(ThrowableRunnable nestedInitializationTask) {
      myNestedInitializationTasks.add(nestedInitializationTask);
    }

    private void runParallelNestedInitializationTasks() throws InterruptedException {
      int numberOfTasksToExecute = myNestedInitializationTasks.size();
      if (numberOfTasksToExecute == 0) return;

      CountDownLatch proceedLatch = new CountDownLatch(numberOfTasksToExecute);

      if (ourDoParallelIndicesInitialization) {
        ExecutorService taskExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor(
          "IndexInfrastructure.DataInitialization.RunParallelNestedInitializationTasks", PooledThreadExecutor.INSTANCE,
          CacheUpdateRunner.indexingThreadCount());

        for (ThrowableRunnable callable : myNestedInitializationTasks) {
          taskExecutor.execute(() -> executeNestedInitializationTask(callable, proceedLatch));
        }

        proceedLatch.await();
        taskExecutor.shutdown();
      }
      else {
        for (ThrowableRunnable callable : myNestedInitializationTasks) {
          executeNestedInitializationTask(callable, proceedLatch);
        }
      }
    }

    private void executeNestedInitializationTask(ThrowableRunnable callable, CountDownLatch proceedLatch) {
      Application app = ApplicationManager.getApplication();
      try {
        // To correctly apply file removals in indices's shutdown hook we should process all initialization tasks
        // Todo: make processing removed files more robust because ignoring 'dispose in progress' delays application exit and
        // may cause memory leaks IDEA-183718, IDEA-169374,
        if (app.isDisposed() /*|| app.isDisposeInProgress()*/) return;
        callable.run();
      }
      catch (Throwable t) {
        onThrowable(t);
      }
      finally {
        proceedLatch.countDown();
      }
    }
  }

  public static boolean hasIndices() {
    return !SystemProperties.is("idea.skip.indices.initialization");
  }
}