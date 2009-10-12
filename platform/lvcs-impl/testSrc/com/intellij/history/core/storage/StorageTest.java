/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.history.core.storage;

import com.intellij.history.core.LocalVcs;
import com.intellij.history.core.TempDirTestCase;
import com.intellij.history.core.changes.ChangeSet;
import com.intellij.history.core.changes.CreateDirectoryChange;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

public class StorageTest extends TempDirTestCase {
  Storage s;
  LocalVcs.Memento m = new LocalVcs.Memento();

  @Before
  public void setUp() {
    initStorage();
  }

  @After
  public void tearDown() {
    if (s != null) s.close();
  }

  @Test
  public void testCleanStorage() {
    m = s.load();

    assertTrue(m.myRoot.getChildren().isEmpty());
    assertEquals(0, m.myEntryCounter);
    assertTrue(m.myChangeList.getChanges().isEmpty());
  }

  @Test
  public void testSaving() {
    ChangeSet cs = cs(new CreateDirectoryChange(1, "dir"));
    cs.applyTo(m.myRoot);
    m.myChangeList.addChange(cs);
    m.myEntryCounter = 11;

    s.saveState(m);

    initStorage();
    m = s.load();

    assertEquals(1, m.myRoot.getChildren().size());
    assertEquals(11, m.myEntryCounter);
    assertEquals(1, m.myChangeList.getChanges().size());
  }

  @Test
  public void testCreatingAbsentDirs() {
    File dir = new File(tempDir, "dir1/dir2/dir3");
    assertFalse(dir.exists());

    m.myEntryCounter = 111;

    initStorage(dir);
    s.saveState(m);

    assertTrue(dir.exists());
  }

  @Test
  public void testCleaningStorageOnVersionChange() {
    initStorage(123);

    m.myEntryCounter = 111;
    s.saveState(m);

    initStorage(666);

    m = s.load();
    assertEquals(0, m.myEntryCounter);
  }

  @Test
  public void testDoesNotCleanStorageWithProperVersion() {
    initStorage(123);

    m.myEntryCounter = 111;
    s.saveState(m);

    initStorage(123);

    m = s.load();
    assertEquals(111, m.myEntryCounter);
  }

  @Test
  public void testCreatingContent() {
    Content c = s.storeContent(b("abc"));
    assertArrayEquals(b("abc"), c.getBytes());
  }

  @Test
  public void testPurgingContents() {
    Content c1 = s.storeContent(b("1"));
    s.storeContent(b("2"));
    Content c3 = s.storeContent(b("3"));
    s.purgeContents(Arrays.asList(c1, c3));

    int id1 = getIdOf(c1);
    int id3 = getIdOf(c3);
    int newId1 = getIdOf(s.storeContent(b("1")));
    int newId3 = getIdOf(s.storeContent(b("3")));
    assertTrue(newId1 == id1 || newId1 == id3);
    assertTrue(newId3 == id1 || newId3 == id3);
  }

  private int getIdOf(Content c) {
    return ((StoredContent)c).getId();
  }

  @Test
  public void testRecreationOfStorageOnLoadingError() {
    StoredContent oldContent = (StoredContent)s.storeContent(b("abc"));
    m.myEntryCounter = 10;
    s.saveState(m);
    s.close();

    corruptFile("storage");

    initStorage();
    m = s.load();
    assertEquals(0, m.myEntryCounter);

    StoredContent newContent = (StoredContent)s.storeContent(b("abc"));
    assertEquals(oldContent.getId(), newContent.getId());
  }

  private void corruptFile(String name) {
    try {
      File f = new File(tempDir, name);
      assertTrue(f.exists());

      FileWriter w = new FileWriter(f);
      w.write("bla-bla-bla");
      w.close();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void testRecreationOfStorageOnContentLoadingError() {
    m.myEntryCounter = 10;
    s.saveState(m);
    s.close();

    initStorage(new MyBrokenStorage());
    try {
      s.loadContentData(666);
      fail();
    }
    catch (BrokenStorageException e) {
    }

    initStorage();
    m = s.load();

    assertEquals(0, m.myEntryCounter);
  }

  @Test
  public void testThrowingExceptionForGoodContentWhenContentStorageIsBroken() {
    initStorage(new MyBrokenStorage());

    try {
      s.loadContentData(666);
      fail();
    }
    catch (BrokenStorageException e) {
    }

    try {
      s.loadContentData(1);
      fail();
    }
    catch (BrokenStorageException e) {
    }
  }

  @Test
  public void testReturningUnavailableContentWhenContentStorageIsBroken() {
    initStorage(new MyBrokenStorage());
    try {
      s.loadContentData(666);
    }
    catch (BrokenStorageException e) {
    }

    Content c = s.storeContent(b("abc"));
    assertEquals(UnavailableContent.class, c.getClass());
  }

  private void initStorage() {
    initStorage(tempDir);
  }

  private void initStorage(File dir) {
    if (s != null) s.close();
    s = new Storage(dir);
  }

  private void initStorage(final int version) {
    if (s != null) s.close();
    initStorage(new Storage(tempDir) {
      @Override
      protected int getVersion() {
        return version;
      }
    });
  }

  private void initStorage(Storage storage) {
    if (s != null) s.close();
    s = storage;
  }

  private class MyBrokenStorage extends Storage {
    public MyBrokenStorage() {
      super(StorageTest.this.tempDir);
    }

    @Override
    protected IContentStorage createContentStorage() throws IOException {
      return new IContentStorage() {
        public void save() {
        }

        public void close() {
        }

        public int store(byte[] content) throws BrokenStorageException {
          return 1;
        }

        public byte[] load(int id) throws BrokenStorageException {
          if (id == 1) {
            return new byte[0];
          }
          else {
            throw new BrokenStorageException();
          }
        }

        public void remove(int id) {

        }

        public void setVersion(int version) {
        }

        public int getVersion() {
          return 1;
        }
      };
    }
  }
}
