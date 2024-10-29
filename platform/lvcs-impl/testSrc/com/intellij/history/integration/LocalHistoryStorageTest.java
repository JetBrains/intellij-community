// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.history.integration;

import com.intellij.history.core.LocalHistoryStorage;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.io.storage.AbstractStorage;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LocalHistoryStorageTest extends IntegrationTestCase {
  private LocalHistoryStorage myStorage;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myStorage = new LocalHistoryStorage(myRoot.toNioPath().resolve("storage"));
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Disposer.dispose(myStorage);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testChangesAccumulationPerformance() throws IOException {
    VirtualFile f = WriteAction.compute(
      () -> VirtualFileManager.getInstance().findFileByUrl("temp:///").createChildData(null, "testChangesAccumulationPerformance.txt")
    );
    try {
      Benchmark.newBenchmark("local history changes accumulation", () -> {
        doChangesAccumulationPerformanceTest(f);
      }).start();
    }
    finally {
      WriteAction.run(() -> {
        f.delete(null);
      });
    }
  }

  private void doChangesAccumulationPerformanceTest(VirtualFile file) {
    for (int i = 0; i < 1000; i++) {
      getVcs().beginChangeSet();
      setContent(file, "content " + i);
      getVcs().endChangeSet("2");
    }
  }

  public void testBasic() throws Exception {
    assertFirstAndLast(0, 0);

    int r1 = createRecord();
    int r2 = createRecord();

    assertFirstAndLast(r1, r2);
    assertRecord(r2, r1, 0);
    assertRecord(r1, 0, r2);
  }

  public void testWritingAfterClose() throws Exception {
    createRecord();
    Disposer.dispose(myStorage);

    try {
      createRecord();
    }
    catch (IOException e) {
      return;
    }
    fail("should have thrown exception");
  }

  public void testTrimming() throws Exception {
    int r1 = createRecord();
    int r2 = createRecord();
    int r3 = createRecord();
    int r4 = createRecord();

    assertFirstAndLast(r1, r4);
    assertRecord(r1, 0, r2);
    assertRecord(r2, r1, r3);
    assertRecord(r3, r2, r4);
    assertRecord(r4, r3, 0);

    myStorage.deleteRecordsUpTo(r2);

    assertFirstAndLast(r3, r4);
    assertRecord(r3, 0, r4);
    assertRecord(r4, r3, 0);

    myStorage.deleteRecordsUpTo(r4);

    assertFirstAndLast(0, 0);
  }

  public void testReopening() throws Exception {
    int r1 = createRecord();
    int r2 = createRecord();
    int r3 = createRecord();
    int r4 = createRecord();

    myStorage.deleteRecordsUpTo(r2);

    Disposer.dispose(myStorage);
    myStorage = new LocalHistoryStorage(myRoot.toNioPath().resolve("storage"));

    assertFirstAndLast(r3, r4);
    assertRecord(r3, 0, r4);
    assertRecord(r4, r3, 0);

    myStorage.deleteRecordsUpTo(r3);

    Disposer.dispose(myStorage);
    myStorage = new LocalHistoryStorage(myRoot.toNioPath().resolve("storage"));

    assertFirstAndLast(r4, r4);
    assertRecord(r4, 0, 0);

    int r5 = createRecord();

    Disposer.dispose(myStorage);
    myStorage = new LocalHistoryStorage(myRoot.toNioPath().resolve("storage"));

    assertFirstAndLast(r4, r5);
    assertRecord(r4, 0, r5);
    assertRecord(r5, r4, 0);
  }

  public void testWritingChangesOfDifferentSize() throws Exception {
    final int MAX = 100;
    List<Integer> records = new ArrayList<>(MAX);
    for (int i = 0; i < MAX; i++) {
      if (i > MAX / 2) {
        myStorage.deleteRecordsUpTo(records.get(records.size() - MAX / 2));
      }
      records.add(createRecord(i*50));
    }

    assertFirstAndLast(records.get(records.size() - MAX / 2), records.get(records.size() - 1));
  }

  private int createRecord() throws IOException {
    return createRecord(1000);
  }

  private int createRecord(int size) throws IOException {
    int r = myStorage.createNextRecord();
    try (AbstractStorage.StorageDataOutput s = myStorage.writeStream(r, true)) {
      for (int i = 0; i < size; i++) {
        s.writeInt(r);
      }
    }
    return r;
  }

  private void assertFirstAndLast(int first, int last) throws IOException {
    assertEquals(first, myStorage.getFirstRecord());
    assertEquals(last, myStorage.getLastRecord());
  }

  private void assertRecord(int id, int prev, int next) throws IOException {
    assertEquals(prev, myStorage.getPrevRecord(id));
    assertEquals(next, myStorage.getNextRecord(id));
    try (DataInputStream s = myStorage.readStream(id)) {
      for (int i = 0; i < 1000; i++) {
        assertEquals(id, s.readInt());
      }
    }
  }
}
