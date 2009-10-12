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