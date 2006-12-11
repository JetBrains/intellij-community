package com.intellij.localvcs;

import com.intellij.util.io.SharedCachingStrategy;
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

    s.storeContent(1, c1);
    s.storeContent(2, c2);

    assertEquals(c1, s.loadContent(1));
    assertEquals(c2, s.loadContent(2));
  }

  @Test(expected = AssertionError.class)
  public void testLoadingUnexistingContentThrowsException() throws Exception {
    s.loadContent(666);
  }

  @Test
  public void testChangingContent() throws Exception {
    byte[] c1 = new byte[]{1, 2, 3};
    byte[] c2 = new byte[]{4, 5, 6};

    s.storeContent(1, c1);
    s.storeContent(1, c2);

    assertEquals(c2, s.loadContent(1));
  }

  @Test
  public void testStoringBetweenSessions() throws Exception {
    byte[] c = new byte[]{1, 2, 3};

    s.storeContent(1, c);
    s.close();

    s = createStorage();
    assertEquals(c, s.loadContent(1));
  }

  @Test
  public void testSaving() throws Exception {
    byte[] c = new byte[]{1, 2, 3};

    s.storeContent(1, c);
    s.save();

    ContentStorage another = createStorage();
    try {
      assertEquals(c, another.loadContent(1));
    }
    finally {
      another.close();
    }
  }

  @Test
  public void testRemoving() throws Exception {
    s.storeContent(1, new byte[] {1});
    assertTrue(s.hasContent(1));

    s.removeContent(1);
    assertFalse(s.hasContent(1));
  }

  @Test(expected = AssertionError.class)
  public void testLoadingRemovedContentThrowsException() throws Exception {
    s.storeContent(1, new byte[] {1});
    s.removeContent(1);

    s.loadContent(1);
  }

  @Test
  public void testSavtingWithRemovedContent() throws Exception {
    s.storeContent(1, new byte[] {1});
    s.removeContent(1);
    s.close();

    s = createStorage();
    assertFalse(s.hasContent(1));
  }

  private void assertEquals(byte[] expected, byte[] actual) {
    assertEquals(expected.length, actual.length);
    for (int i = 0; i < expected.length; i++) {
      assertEquals(expected[i], actual[i]);
    }
  }
}
