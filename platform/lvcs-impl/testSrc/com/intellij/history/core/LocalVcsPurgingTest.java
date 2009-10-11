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

import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.storage.Content;
import com.intellij.history.core.storage.StoredContent;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class LocalVcsPurgingTest extends LocalVcsTestCase {
  InMemoryLocalVcs vcs = new InMemoryLocalVcs(new PurgeLoggingStorage());
  List<Content> purgedContent = new ArrayList<Content>();

  @Before
  public void setUp() {
    setCurrentTimestamp(10);
    long timestamp = -1;
    vcs.createFile("file", cf("one"), timestamp, false);

    setCurrentTimestamp(20);
    vcs.changeFileContent("file", cf("two"), -1);

    setCurrentTimestamp(30);
    vcs.changeFileContent("file", cf("three"), -1);

    setCurrentTimestamp(40);
    vcs.changeFileContent("file", cf("four"), -1);
  }

  @Test
  public void testPurging() {
    assertEquals(4, vcs.getRevisionsFor("file").size());

    vcs.purgeObsoleteAndSave(5);

    List<Revision> rr = vcs.getRevisionsFor("file");
    assertEquals(2, rr.size());
    assertEquals(40L, rr.get(0).getTimestamp());
  }

  @Test
  public void testPurgingContents() {
    vcs.purgeObsoleteAndSave(5);

    assertEquals(2, purgedContent.size());
    assertEquals(c("one"), purgedContent.get(0));
    assertEquals(c("two"), purgedContent.get(1));
  }

  @Test
  public void testDoesNotPurgeLongContentFromContentStorage() {
    vcs = new InMemoryLocalVcs(new PurgeLoggingStorage());
    setCurrentTimestamp(10);
    long timestamp = -1;
    vcs.createFile("file", bigContentFactory(), timestamp, false);

    setCurrentTimestamp(20);
    vcs.changeFileContent("file", cf("one"), -1);

    setCurrentTimestamp(30);
    vcs.changeFileContent("file", cf("twoo"), -1);

    vcs.purgeObsoleteAndSave(5);

    assertTrue(purgedContent.isEmpty());
  }

  class PurgeLoggingStorage extends InMemoryStorage {
    @Override
    public void purgeContent(StoredContent c) {
      purgedContent.add(c);
    }
  }
}