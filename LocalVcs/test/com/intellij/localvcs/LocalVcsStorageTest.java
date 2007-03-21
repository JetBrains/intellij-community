package com.intellij.localvcs;

import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;

public class LocalVcsStorageTest extends TempDirTestCase {
  LocalVcsStorage s;
  LocalVcs.Memento m = new LocalVcs.Memento();

  @After
  public void tearDown() {
    if (s != null) s.close();
  }

  @Test
  public void testCleanStorage() {
    s = new LocalVcsStorage(tempDir);
    m = s.load();

    assertTrue(m.myRoot.getChildren().isEmpty());
    assertEquals(0, m.myEntryCounter);
    assertTrue(m.myChangeList.getChangeSets().isEmpty());
  }

  @Test
  public void testCreatingAbsentDirs() {
    File dir = new File(tempDir, "dir1/dir2/dir3");
    assertFalse(dir.exists());

    m.myEntryCounter = 111;
    s = new LocalVcsStorage(dir);
    s.store(m);

    assertTrue(dir.exists());
  }

  @Test
  public void testCleaningStorageOnVersionChange() {
    s = new LocalVcsStorage(tempDir) {
      @Override
      protected int getVersion() {
        return 123;
      }
    };

    m.myEntryCounter = 111;
    s.store(m);
    s.close();

    s = new LocalVcsStorage(tempDir) {
      @Override
      protected int getVersion() {
        return 666;
      }
    };

    m = s.load();
    assertEquals(0, m.myEntryCounter);
  }

  @Test
  public void testDoesNotCleanStorageWithProperVersion() {
    s = new LocalVcsStorage(tempDir) {
      @Override
      protected int getVersion() {
        return 123;
      }
    };

    m.myEntryCounter = 111;
    s.store(m);
    s.close();

    s = new LocalVcsStorage(tempDir) {
      @Override
      protected int getVersion() {
        return 123;
      }
    };

    m = s.load();
    assertEquals(111, m.myEntryCounter);
  }

  @Test
  public void testCreatingContent() {
    s = new LocalVcsStorage(tempDir);

    Content c = s.createContent(new byte[]{1, 2, 3});
    assertEquals(new byte[]{1, 2, 3}, c.getBytes());
  }

  @Test
  public void testCreatingLongContent() {
    s = new LocalVcsStorage(tempDir);

    Content c = s.createContent(new byte[LongContent.MAX_LENGTH + 1]);
    assertEquals(LongContent.class, c.getClass());
  }

  @Test
  public void testPurgingContents() {
    s = new LocalVcsStorage(tempDir);
    Content c1 = s.createContent(b("1"));
    Content c2 = s.createContent(b("2"));
    Content c3 = s.createContent(b("3"));
    s.purgeContents(Arrays.asList(c1, c3));

    assertTrue(s.isContentPurged(c1));
    assertFalse(s.isContentPurged(c2));
    assertTrue(s.isContentPurged(c3));
  }
}
