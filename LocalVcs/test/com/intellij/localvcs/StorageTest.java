package com.intellij.localvcs;

import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;

public class StorageTest extends TempDirTestCase {
  private Storage s;

  @After
  public void tearDown() {
    if (s != null) s.close();
  }

  @Test
  public void testCleanStorage() {
    s = new Storage(tempDir);

    ChangeList changeList = s.loadChangeList();
    RootEntry entry = s.loadRootEntry();
    Integer counter = s.loadCounter();

    assertTrue(changeList.getChangeSets().isEmpty());
    assertTrue(entry.getChildren().isEmpty());
    assertEquals(0, counter);
  }

  @Test
  public void testCleaningStorageOnVersionChange() {
    s = new Storage(tempDir) {
      @Override
      protected int getVersion() {
        return 123;
      }
    };
    s.storeCounter(111);
    s.close();

    s = new Storage(tempDir) {
      @Override
      protected int getVersion() {
        return 666;
      }
    };

    assertEquals(0, s.loadCounter());
  }

  @Test
  public void testDoesNotCleanStorageWithProperVersion() {
    s = new Storage(tempDir) {
      @Override
      protected int getVersion() {
        return 123;
      }
    };
    s.storeCounter(111);
    s.close();

    s = new Storage(tempDir) {
      @Override
      protected int getVersion() {
        return 123;
      }
    };

    assertEquals(111, s.loadCounter());
  }

  @Test
  public void testCreatingAbsentDirs() {
    File dir = new File(tempDir, "dir1/dir2/dir3");
    assertFalse(dir.exists());

    s = new Storage(dir);
    s.storeCounter(1);
    assertTrue(dir.exists());
  }

  @Test
  public void testCreatingContent() {
    s = new Storage(tempDir);

    Content c = s.createContent(new byte[]{1, 2, 3});
    assertEquals(new byte[]{1, 2, 3}, c.getBytes());
  }

  @Test
  public void testCreatingLongContent() {
    s = new Storage(tempDir);

    Content c = s.createContent(new byte[LongContent.MAX_LENGTH + 1]);
    assertEquals(LongContent.class, c.getClass());
  }

  @Test
  public void testPurgingContents() {
    s = new Storage(tempDir);
    Content c1 = s.createContent(b("1"));
    Content c2 = s.createContent(b("2"));
    Content c3 = s.createContent(b("3"));
    s.purgeContents(Arrays.asList(c1, c3));

    assertTrue(s.isContentPurged(c1));
    assertFalse(s.isContentPurged(c2));
    assertTrue(s.isContentPurged(c3));
  }
}
