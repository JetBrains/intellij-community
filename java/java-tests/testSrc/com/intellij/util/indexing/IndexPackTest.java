// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.command.impl.DummyProject;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.indexing.impl.MapIndexStorage;
import com.intellij.util.indexing.impl.MapReduceIndex;
import com.intellij.util.indexing.impl.ReadOnlyIndexPack;
import com.intellij.util.indexing.impl.forward.KeyCollectionForwardIndexAccessor;
import com.intellij.util.indexing.impl.forward.PersistentMapBasedForwardIndex;
import com.intellij.util.indexing.zipFs.UncompressedZipFileSystem;
import com.intellij.util.indexing.zipFs.UncompressedZipFileSystemProvider;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.StringEnumeratorTest;
import com.intellij.util.io.zip.JBZipFile;
import gnu.trove.TIntHashSet;
import junit.framework.AssertionFailedError;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Ignore
public class IndexPackTest extends TestCase {
  private static final Logger LOG = Logger.getInstance(IndexPackTest.class);

  public void testSimpleIndexPack() throws IOException, StorageException {
    File dir = FileUtil.createTempDirectory("persistent-map", "packs");

    MapReduceIndex<String, String, String> m1 = createSplitStringIndex(dir.toPath().resolve("index1").resolve("index"), false);
    MapReduceIndex<String, String, String> m2 = createSplitStringIndex(dir.toPath().resolve("index2").resolve("index"), false);

    assertTrue(m1.update(1, "key/value").compute());
    assertTrue(m1.update(2, "key2/value2").compute());
    assertTrue(m1.update(3, "key/value2").compute());

    assertTrue(m2.update(1, "key/value").compute());
    assertTrue(m2.update(2, "key2/value2").compute());
    assertTrue(m2.update(3, "key/value2").compute());

    m1.dispose();
    m2.dispose();

    File pack = new File(dir, "pack.zip");
    try (JBZipFile file = new JBZipFile(pack)) {
      for (String index : Arrays.asList("index1", "index2")) {
        for (Path path : Files.newDirectoryStream(dir.toPath().resolve(index))) {
          file.getOrCreateEntry(index + "/" + path.getFileName().toString()).setDataFromFile(path.toFile());
        }
      }
    }

    MultiMap<String, Pair<String, Integer>> indexMap1 = new MultiMap<>();
    indexMap1.putValue("key", Pair.create("value", 1));
    indexMap1.putValue("key", Pair.create("value2", 3));
    indexMap1.putValue("key2", Pair.create("value2", 2));

    MultiMap<String, Pair<String, Integer>> indexMap2 = new MultiMap<>();
    indexMap2.putValue("key", Pair.create("value", 1));
    indexMap2.putValue("key", Pair.create("value2", 3));
    indexMap2.putValue("key2", Pair.create("value2", 2));

    try (UncompressedZipFileSystem fs = new UncompressedZipFileSystem(pack.toPath(), new UncompressedZipFileSystemProvider())) {
      MapReduceIndex<String, String, String> mm1 = createSplitStringIndex(fs.getPath("index1").resolve("index"), true);
      MapReduceIndex<String, String, String> mm2 = createSplitStringIndex(fs.getPath("index2").resolve("index"), true);

      for (String key : new ArrayList<>(indexMap1.keySet())) {
        mm1.getData(key).forEach((id, value) -> {
          indexMap1.remove(key, Pair.create(value, id));
          return true;
        });
      }

      for (String key : new ArrayList<>(indexMap2.keySet())) {
        mm2.getData(key).forEach((id, value) -> {
          indexMap2.remove(key, Pair.create(value, id));
          return true;
        });
      }

      mm1.dispose();
      mm2.dispose();
    }

    assertTrue(indexMap1.isEmpty());
    assertTrue(indexMap2.isEmpty());
  }

  private static final int sampleCount = 1000;

  public void testIndexAccessPerformance() throws IOException {
    File dir = FileUtil.createTempDirectory("persistent-map", "packs");

    TIntHashSet keys = new TIntHashSet();
    InvertedIndex<String, String, String> index = createStringLengthIndex(dir.toPath().resolve("index"), false);
    for (int i = 1; i <= sampleCount; ++i) {
      final String string = generateString();
      keys.add(string.length());
      index.update(i, string).compute();
    }
    index.dispose();

    PlatformTestUtil.startPerformanceTest("read", 2000, () -> {
      InvertedIndex<String, String, String> readIndex = createStringLengthIndex(dir.toPath().resolve("index"), true);
      LongAdder recordCount = new LongAdder();
      for (int key : keys.toArray()) {
        readIndex.getData(String.valueOf(key)).forEach((id, value) -> {
          recordCount.increment();
          return true;
        });
      }
      assertEquals(sampleCount, recordCount.intValue());
      readIndex.dispose();
    }).attempts(5).ioBound().assertTiming();
  }

  public void testTrivialPackAccessPerformance() throws IOException {
    File dir = FileUtil.createTempDirectory("persistent-map", "packs");

    TIntHashSet keys = new TIntHashSet();
    InvertedIndex<String, String, String> index = createStringLengthIndex(dir.toPath().resolve("index").resolve("index"), false);
    for (int i = 1; i <= sampleCount; ++i) {
      final String string = generateString();
      keys.add(string.length());
      index.update(i, string).compute();
    }
    index.dispose();

    File pack = new File(dir, "pack.zip");
    try (JBZipFile file = new JBZipFile(pack)) {
      for (Path path : Files.newDirectoryStream(dir.toPath().resolve("index"))) {
        file.getOrCreateEntry("index/" + path.getFileName().toString()).setDataFromFile(path.toFile());
      }
    }

    try (UncompressedZipFileSystem fs = new UncompressedZipFileSystem(pack.toPath(), new UncompressedZipFileSystemProvider())) {
      PlatformTestUtil.startPerformanceTest("read", 2000, () -> {
        InvertedIndex<String, String, String> readIndex = createStringLengthIndex(fs.getPath("index", "index"), true);
        LongAdder recordCount = new LongAdder();
        for (int key : keys.toArray()) {
          readIndex.getData(String.valueOf(key)).forEach((id, value) -> {
            recordCount.increment();
            return true;
          });
        }
        assertEquals(sampleCount, recordCount.intValue());
        readIndex.dispose();
      }).attempts(5).ioBound().assertTiming();
    }
  }

  public void test1000PackAccessPerformance() throws IOException {
    File dir = FileUtil.createTempDirectory("persistent-map", "packs");

    TIntHashSet keys = new TIntHashSet();
    int packSize = 1000;
    List<InvertedIndex<String, String, String>> indexes = generateIndexNames(packSize)
      .map(name -> createStringLengthIndex(dir.toPath().resolve(name).resolve("index"), false))
      .collect(Collectors.toList());
    for (int i = 1; i <= sampleCount; ++i) {
      final String string = generateString();
      keys.add(string.length());
      indexes.get(Math.abs(string.hashCode() % packSize)).update(i, string).compute();
    }
    indexes.forEach(InvertedIndex::dispose);

    File pack = new File(dir, "pack.zip");
    try (JBZipFile file = new JBZipFile(pack)) {
      generateIndexNames(packSize).map(name -> dir.toPath().resolve(name)).forEach(p -> {
        try {
          for (Path path : Files.newDirectoryStream(p)) {
            file.getOrCreateEntry(path.getParent().getFileName().toString() + "/" + path.getFileName().toString()).setDataFromFile(path.toFile());
          }
        }
        catch (IOException e) {
          LOG.error(e);
          throw new AssertionFailedError(e.getMessage());
        }
      });
    }

    try (UncompressedZipFileSystem fs = new UncompressedZipFileSystem(pack.toPath(), new UncompressedZipFileSystemProvider())) {
      PlatformTestUtil.startPerformanceTest("read", 6000, () -> {

        ReadOnlyIndexPack<String, String, String, InvertedIndex<String, String, String>> indexPack
                = new ReadOnlyIndexPack<>(path -> createStringLengthIndex(path, false));

        generateIndexNames(packSize).map(name -> fs.getPath(name, "index")).forEach(path -> {
          indexPack.attach(path, DummyProject.getInstance());
        });

        LongAdder recordCount = new LongAdder();
        for (int key : keys.toArray()) {
          indexPack.getData(String.valueOf(key)).forEach((id, value) -> {
            recordCount.increment();
            return true;
          });
        }
        assertEquals(sampleCount, recordCount.intValue());
        indexPack.dispose();
      }).attempts(5).ioBound().assertTiming();
    }
  }

  @NotNull
  private static Stream<String> generateIndexNames(int packSize) {
    return IntStream
      .rangeClosed(1, packSize)
      .mapToObj(idx -> "index" + idx);
  }

  @NotNull
  private static String generateString() {
    return StringUtil.repeat(StringEnumeratorTest.createRandomString(), 30);
  }

  private static MapReduceIndex<String, String, String> createSplitStringIndex(@NotNull Path path, boolean readOnly) throws IOException {
    return createIndex(path, readOnly, new DataIndexer<String, String, String>() {
      @NotNull
      @Override
      public Map<String, String> map(@NotNull String inputData) {
        String[] split = inputData.split("/");
        return Collections.singletonMap(split[0], split[1]);
      }
    });
  }


  private static InvertedIndex<String, String, String> createStringLengthIndex(@NotNull Path path, boolean readOnly) {
    try {
      return createIndex(path, readOnly, new DataIndexer<String, String, String>() {
        @NotNull
        @Override
        public Map<String, String> map(@NotNull String inputData) {
          return Collections.singletonMap(String.valueOf(inputData.length()), inputData + "_value");
        }
      });
    }
    catch (IOException e) {
      LOG.error(e);
      throw new AssertionFailedError(e.getMessage());
    }
  }

  private static MapReduceIndex<String, String, String> createIndex(@NotNull Path path, boolean readOnly, DataIndexer<String, String, String> indexer) throws IOException {
    IndexExtension<String, String, String> extension = new IndexExtension<String, String, String>() {
      @NotNull
      @Override
      public IndexId<String, String> getName() {
        return IndexId.create("AnIndex");
      }

      @NotNull
      @Override
      public DataIndexer<String, String, String> getIndexer() {
        return indexer;
      }

      @NotNull
      @Override
      public KeyDescriptor<String> getKeyDescriptor() {
        return EnumeratorStringDescriptor.INSTANCE;
      }

      @NotNull
      @Override
      public DataExternalizer<String> getValueExternalizer() {
        return EnumeratorStringDescriptor.INSTANCE;
      }

      @Override
      public int getVersion() {
        return 0;
      }
    };
    return new MapReduceIndex<String, String, String>(extension, new MapIndexStorage<String, String>(path.getParent().resolve(path.getFileName() + ".storage"), EnumeratorStringDescriptor.INSTANCE,
                                           EnumeratorStringDescriptor.INSTANCE, 1024, false, true, readOnly, null) {
      @Override
      protected void checkCanceled() {

      }
    }, new PersistentMapBasedForwardIndex(path.getParent().resolve(path.getFileName() + ".forward"), readOnly), new KeyCollectionForwardIndexAccessor<>(extension)) {
      @Override
      public void checkCanceled() {

      }

      @Override
      protected void requestRebuild(@NotNull Throwable e) {
        e.printStackTrace();
        fail();
      }
    };
  }
}
