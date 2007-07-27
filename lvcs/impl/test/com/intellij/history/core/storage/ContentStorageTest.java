package com.intellij.history.core.storage;

import com.intellij.history.core.TempDirTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
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

    assertArrayEquals(c1, s.load(id1));
    assertArrayEquals(c2, s.load(id2));
  }

  @Test
  public void testStoringBetweenSessions() throws Exception {
    byte[] c = new byte[]{1, 2, 3};

    int id = s.store(c);
    s.close();

    s = createStorage();
    assertArrayEquals(c, s.load(id));
  }

  @Test
  public void testSavingOnClose() throws Exception {
    byte[] c = new byte[]{1, 2, 3};

    int id = s.store(c);
    s.close();

    IContentStorage another = createStorage();
    try {
      assertArrayEquals(c, another.load(id));
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
}