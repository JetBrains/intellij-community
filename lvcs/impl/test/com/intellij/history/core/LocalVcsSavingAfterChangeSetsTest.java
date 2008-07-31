package com.intellij.history.core;

import com.intellij.history.core.storage.Storage;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class LocalVcsSavingAfterChangeSetsTest extends TempDirTestCase {
  private LocalVcs vcs;
  private Storage s;
  boolean called = false;

  @Before
  public void initVcs() {
    s = new Storage(tempDir) {
      @Override
      public void saveContents() {
        called = true;
        super.saveContents();
      }
    };
    vcs = new LocalVcs(s);
  }

  @After
  public void closeStorage() {
    s.close();
  }

  @Test
  public void testCallingStorageSaveOnChangeSetEnd() {
    vcs.beginChangeSet();
    assertFalse(called);

    vcs.endChangeSet(null);
    assertTrue(called);
  }

  @Test
  public void testCallingStorageSaveOnlyOnOuterChangeSetEnd() {
    vcs.beginChangeSet();
    vcs.beginChangeSet();
    vcs.endChangeSet(null);
    assertFalse(called);

    vcs.endChangeSet(null);
    assertTrue(called);
  }

  @Test
  public void testCorrectlyHandleSequencedInnerChangeSets() {
    vcs.beginChangeSet();
    vcs.beginChangeSet();
    vcs.endChangeSet(null);
    assertFalse(called);

    vcs.endChangeSet(null);
    assertTrue(called);

    called = false;

    vcs.beginChangeSet();
    vcs.beginChangeSet();
    vcs.endChangeSet(null);
    assertFalse(called);

    vcs.endChangeSet(null);
    assertTrue(called);
  }

  @Test
  public void testCallingStorageSaveAfterSingleChange() {
    vcs.createDirectory("dir");
    assertTrue(called);
  }

  @Test
  public void testDoesNotCallStorageSaveAfterChangesInsideChangeSet() {
    vcs.beginChangeSet();
    vcs.createDirectory("dir1");
    vcs.createDirectory("dir2");
    assertFalse(called);

    vcs.endChangeSet(null);
    assertTrue(called);
  }
}