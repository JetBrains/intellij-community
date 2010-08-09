/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.historyIntegrTests;

import com.intellij.history.core.LinkedStorage;
import com.intellij.util.io.storage.AbstractStorage;

import java.io.DataInputStream;
import java.io.IOException;

public class LinkedStorageTest extends IntegrationTestCase {
  private LinkedStorage myStorage;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myStorage = new LinkedStorage(myRoot.getPath() + "/storage");
  }

  @Override
  protected void tearDown() throws Exception {
    myStorage.dispose();
    super.tearDown();
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
    myStorage.dispose();

    try {
      createRecord();
    }
    catch (AssertionError e) {
      return;
    }
    fail("should have thrown exception");
  }

  public void testDeletion() throws Exception {
    int r1 = createRecord();
    int r2 = createRecord();
    int r3 = createRecord();

    assertFirstAndLast(r1, r3);
    assertRecord(r3, r2, 0);
    assertRecord(r2, r1, r3);
    assertRecord(r1, 0, r2);

    myStorage.deleteRecord(r2);

    assertFirstAndLast(r1, r3);
    assertRecord(r3, r1, 0);
    assertRecord(r1, 0, r3);

    myStorage.deleteRecord(r3);

    assertFirstAndLast(r1, r1);
    assertRecord(r1, 0, 0);

    int r4 = createRecord();

    assertFirstAndLast(r1, r4);
    assertRecord(r4, r1, 0);
    assertRecord(r1, 0, r4);

    myStorage.deleteRecord(r1);

    assertFirstAndLast(r4, r4);
    assertRecord(r4, 0, 0);

    myStorage.deleteRecord(r4);

    assertFirstAndLast(0, 0);
  }

  public void testReopening() throws Exception {
    int r1 = createRecord();
    int r2 = createRecord();
    int r3 = createRecord();

    myStorage.deleteRecord(r2);

    myStorage.dispose();
    myStorage = new LinkedStorage(myRoot.getPath() + "/storage");

    assertFirstAndLast(r1, r3);
    assertRecord(r3, r1, 0);
    assertRecord(r1, 0, r3);

    myStorage.deleteRecord(r1);

    myStorage.dispose();
    myStorage = new LinkedStorage(myRoot.getPath() + "/storage");

    assertFirstAndLast(r3, r3);
    assertRecord(r3, 0, 0);

    int r4 = createRecord();

    myStorage.dispose();
    myStorage = new LinkedStorage(myRoot.getPath() + "/storage");

    assertFirstAndLast(r3, r4);
    assertRecord(r4, r3, 0);
    assertRecord(r3, 0, r4);
  }

  private int createRecord() throws IOException {
    int r = myStorage.createNextRecord();
    AbstractStorage.StorageDataOutput s = myStorage.writeStream(r);
    for (int i = 0; i < 1000; i++) {
      s.writeInt(r);
    }
    s.close();
    return r;
  }

  private void assertFirstAndLast(int first, int last) {
    assertEquals(first, myStorage.getFirstRecord());
    assertEquals(last, myStorage.getLastRecord());
  }

  private void assertRecord(int id, int prev, int next) throws IOException {
    assertEquals(prev, myStorage.getPrevRecord(id));
    assertEquals(next, myStorage.getNextRecord(id));
    DataInputStream s = myStorage.readStream(id);
    try {
      for (int i = 0; i < 1000; i++) {
        assertEquals(id, s.readInt());
      }
    }
    finally {
      s.close();
    }
  }
}
