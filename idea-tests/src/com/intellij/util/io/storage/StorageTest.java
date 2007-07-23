/*
 * @author max
 */
package com.intellij.util.io.storage;

import com.intellij.openapi.util.Disposer;
import junit.framework.TestCase;

import java.io.File;

public class StorageTest extends TestCase {
  private Storage myStorage;

  protected void setUp() throws Exception {
    super.setUp();
    myStorage = Storage.create(getFileName());
  }

  private static String getFileName() {
    return System.getProperty("java.io.tmpdir") + File.separatorChar + "storagetest";
  }

  protected void tearDown() throws Exception {
    Disposer.dispose(myStorage);
    Storage.deleteFiles(getFileName());
    super.tearDown();
  }

  public void testSmoke() throws Exception {
    final int record = myStorage.createNewRecord();
    myStorage.writeBytes(record, "Hello".getBytes());
    assertEquals("Hello", new String(myStorage.readBytes(record)));
  }

  public void testStress() throws Exception {
    StringBuffer data = new StringBuffer();
    for (int i = 0; i < 100; i++) {
      data.append("Hello ");
    }
    String hello = data.toString();

    long start = System.currentTimeMillis();
    final int count = 100000;
    int[] records = new int[count];

    for (int i = 0; i < count; i++) {
      final int record = myStorage.createNewRecord();
      myStorage.writeBytes(record, hello.getBytes());
      records[i] = record;
    }

    for (int record : records) {
      assertEquals(hello, new String(myStorage.readBytes(record)));
    }

    long timedelta = System.currentTimeMillis() - start;
    System.out.println("Done for " + timedelta + "msec.");
  }


}