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

    int id1 = s.storeContent(c1);
    int id2 = s.storeContent(c2);

    assertEquals(c1, s.loadContent(id1));
    assertEquals(c2, s.loadContent(id2));
  }

  @Test(expected = AssertionError.class)
  public void testLoadingUnexistingContentThrowsException() throws Exception {
    s.loadContent(666);
  }

  @Test
  public void testStoringBetweenSessions() throws Exception {
    byte[] c = new byte[]{1, 2, 3};

    int id = s.storeContent(c);
    s.close();

    s = createStorage();
    assertEquals(c, s.loadContent(id));
  }

  @Test
  public void testSaving() throws Exception {
    byte[] c = new byte[]{1, 2, 3};

    int id = s.storeContent(c);
    s.save();

    ContentStorage another = createStorage();
    try {
      assertEquals(c, another.loadContent(id));
    }
    finally {
      another.close();
    }
  }

  @Test
  public void testRemoving() throws Exception {
    int id = s.storeContent(new byte[]{1});
    assertTrue(s.hasContent(id));

    s.removeContent(id);
    assertFalse(s.hasContent(id));
  }

  @Test(expected = AssertionError.class)
  public void testLoadingRemovedContentThrowsException() throws Exception {
    int id = s.storeContent(new byte[]{1});
    s.removeContent(id);

    s.loadContent(id);
  }

  @Test
  public void testSavingWithRemovedContent() throws Exception {
    int id = s.storeContent(new byte[]{1});
    s.removeContent(id);
    s.close();

    s = createStorage();
    assertFalse(s.hasContent(id));
  }
}
