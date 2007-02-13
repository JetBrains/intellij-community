package com.intellij.localvcs;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

public class ContentStorageTest extends TempDirTestCase {
  private ContentStorage s;

  @Before
  public void setUp() throws Exception {
    s = createStorage();
  }

  private ContentStorage createStorage() throws Exception {
    return new ContentStorage(new File(tempDir, "storage"));
  }

  @After
  public void tearDown() throws Exception {
    s.close();
  }

  @Test
  public void testStoring() throws Exception {
    byte[] c1 = new byte[]{1};
    byte[] c2 = new byte[]{2};

    int id1 = s.store(c1);
    int id2 = s.store(c2);

    assertEquals(c1, s.load(id1));
    assertEquals(c2, s.load(id2));
  }

  @Test(expected = AssertionError.class)
  public void testLoadingUnexistingContentThrowsException() throws Exception {
    s.load(666);
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

    ContentStorage another = createStorage();
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
    assertTrue(s.has(id));

    s.remove(id);
    assertFalse(s.has(id));
  }

  @Test(expected = AssertionError.class)
  public void testLoadingRemovedContentThrowsException() throws Exception {
    int id = s.store(new byte[]{1});
    s.remove(id);

    s.load(id);
  }

  @Test
  public void testSavingWithRemovedContent() throws Exception {
    int id = s.store(new byte[]{1});
    s.remove(id);
    s.close();

    s = createStorage();
    assertFalse(s.has(id));
  }
}
