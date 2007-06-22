package com.intellij.history.core.storage;

import com.intellij.history.core.TempDirTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class ContentStorageTest extends TempDirTestCase {
  private ContentStorage s;

  @Before
  public void setUp() throws Exception {
    s = createStorage();
  }

  private ContentStorage createStorage() throws Exception {
    return new ContentStorage(getStorageFile());
  }

  private File getStorageFile() {
    return new File(tempDir, "storage");
  }

  @After
  public void tearDown() {
    s.close();
  }

  @Test
  public void testStoring() throws Exception {
    byte[] c1 = new byte[]{1};
    byte[] c2 = new byte[]{22};

    int id1 = s.store(c1);
    int id2 = s.store(c2);

    assertEquals(c1, s.load(id1));
    assertEquals(c2, s.load(id2));
  }

  @Test
  public void testStoringBetweenSessions() throws Exception {
    byte[] c = new byte[]{1, 2, 3};

    int id = s.store(c);
    s.close();

    s = createStorage();
    assertEquals(c, s.load(id));
  }

  @Test
  public void testSaving() throws Exception {
    byte[] c = new byte[]{1, 2, 3};

    int id = s.store(c);
    s.save();

    IContentStorage another = createStorage();
    try {
      assertEquals(c, another.load(id));
    }
    finally {
      another.close();
    }
  }

  @Test
  public void testRemoving() throws Exception {
    int id = s.store(new byte[]{1});
    s.remove(id);
    assertEquals(id, s.store(new byte[]{1}));
  }

  @Test
  public void testThrowingIOExceptionWhenAskingForInvalidContent() {
    try {
      s.load(123);
      fail();
    }
    catch (IOException e) {
    }
  }

  @Test
  public void testThrowingIOExceptionWhenStorageIsCorrupted() throws IOException {
    int id = s.store("abc".getBytes());
    s.close();

    corruptStorageFile();

    try {
      s.load(id);
      fail();
    }
    catch (IOException e) {
    }

    try {
      s.store("abc".getBytes());
      fail();
    }
    catch (IOException e) {
    }
  }

  private void corruptStorageFile() throws IOException {
    File f = getStorageFile();
    f.delete();
    f.createNewFile();
    FileWriter w = new FileWriter(f);
    w.write("bla-bla-bla");
    w.close();
  }
}