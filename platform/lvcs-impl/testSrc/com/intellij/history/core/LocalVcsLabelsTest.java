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

import com.intellij.history.core.changes.PutLabelChange;
import com.intellij.history.core.changes.PutSystemLabelChange;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.Label;
import org.junit.Test;

import java.util.List;

public class LocalVcsLabelsTest extends LocalVcsTestCase {
  LocalVcs vcs = new InMemoryLocalVcs();

  @Test
  public void testUserLabels() {
    vcs.createFile("file", null, -1, false);
    vcs.putUserLabel("file", "1");
    vcs.changeFileContent("file", null, -1);
    vcs.putUserLabel("file", "2");

    List<Revision> rr = vcs.getRevisionsFor("file");
    assertEquals(4, rr.size());

    assertEquals("2", rr.get(0).getName());
    assertNull(rr.get(1).getName());
    assertEquals("1", rr.get(2).getName());
    assertNull(rr.get(3).getName());
  }

  @Test
  public void testDoesNotIncludeLabelsForAnotherEntry() {
    vcs.createFile("one", null, -1, false);
    vcs.createFile("two", null, -1, false);
    vcs.putUserLabel("one", "one");
    vcs.putUserLabel("two", "two");

    List<Revision> rr = vcs.getRevisionsFor("one");
    assertEquals(2, rr.size());
    assertEquals("one", rr.get(0).getName());

    rr = vcs.getRevisionsFor("two");
    assertEquals(2, rr.size());
    assertEquals("two", rr.get(0).getName());
  }

  @Test
  public void testLabelTimestamps() {
    setCurrentTimestamp(10);
    vcs.createFile("file", null, -1, false);
    setCurrentTimestamp(20);
    vcs.putUserLabel("file", "1");
    setCurrentTimestamp(30);
    vcs.putUserLabel("file", "1");

    List<Revision> rr = vcs.getRevisionsFor("file");
    assertEquals(30, rr.get(0).getTimestamp());
    assertEquals(20, rr.get(1).getTimestamp());
    assertEquals(10, rr.get(2).getTimestamp());
  }

  @Test
  public void testContent() {
    vcs.createFile("file", cf("old"), -1, false);
    vcs.putUserLabel("file", "");
    vcs.changeFileContent("file", cf("new"), -1);
    vcs.putUserLabel("file", "");

    List<Revision> rr = vcs.getRevisionsFor("file");

    assertEquals(c("new"), rr.get(0).getEntry().getContent());
    assertEquals(c("old"), rr.get(2).getEntry().getContent());
  }

  @Test
  public void testLabelsAfterPurge() {
    setCurrentTimestamp(10);
    vcs.createFile("file", null, -1, false);
    setCurrentTimestamp(20);
    vcs.putUserLabel("file", "l");

    vcs.purgeObsoleteAndSave(5);

    List<Revision> rr = vcs.getRevisionsFor("file");
    assertEquals(1, rr.size());
    assertEquals("l", rr.get(0).getName());
  }

  @Test
  public void testGlobalUserLabels() {
    vcs.createFile("one", null, -1, false);
    vcs.putUserLabel("1");
    vcs.createFile("two", null, -1, false);
    vcs.putUserLabel("2");

    List<Revision> rr = vcs.getRevisionsFor("one");
    assertEquals(3, rr.size());
    assertEquals("2", rr.get(0).getName());
    assertEquals("1", rr.get(1).getName());

    rr = vcs.getRevisionsFor("two");
    assertEquals(2, rr.size());
    assertEquals("2", rr.get(0).getName());
  }

  @Test
  public void testGlobalLabelTimestamps() {
    setCurrentTimestamp(10);
    vcs.createFile("file", null, -1, false);
    setCurrentTimestamp(20);
    vcs.putUserLabel("");

    List<Revision> rr = vcs.getRevisionsFor("file");
    assertEquals(20, rr.get(0).getTimestamp());
    assertEquals(10, rr.get(1).getTimestamp());
  }

  @Test
  public void testLabelsDuringChangeSet() {
    vcs.createFile("f", null, -1, false);
    vcs.beginChangeSet();
    vcs.changeFileContent("f", null, -1);
    vcs.putUserLabel("label");
    vcs.endChangeSet("changeSet");

    List<Revision> rr = vcs.getRevisionsFor("f");
    assertEquals(2, rr.size());
    assertEquals("changeSet", rr.get(0).getCauseChangeName());
    assertEquals(null, rr.get(1).getCauseChangeName());
  }

  @Test
  public void testSystemLabels() {
    vcs.createFile("f1", null, -1, false);
    vcs.createFile("f2", null, -1, false);

    setCurrentTimestamp(123);
    vcs.putSystemLabel("label", 456);

    List<Revision> rr1 = vcs.getRevisionsFor("f1");
    List<Revision> rr2 = vcs.getRevisionsFor("f2");
    assertEquals(2, rr1.size());
    assertEquals(2, rr2.size());

    assertEquals("label", rr1.get(0).getName());
    assertEquals("label", rr2.get(0).getName());

    PutLabelChange l = (PutLabelChange)rr1.get(0).getCauseChange();
    assertTrue(l.isSystemLabel());
    assertEquals(123, l.getTimestamp());
    assertEquals(456, ((PutSystemLabelChange)l).getColor());
  }

  @Test
  public void testGettingByteContent() throws Exception {
    Label l1 = vcs.putSystemLabel("label", -1);
    vcs.createFile("f1", cf("one"), -1, false);
    Label l2 = vcs.putSystemLabel("label", -1);
    vcs.changeFileContent("f1", cf("two"), -1);
    vcs.createDirectory("dir");
    Label l3 = vcs.putSystemLabel("label", -1);

    assertNull(l1.getByteContent("f1").getBytes());
    assertEquals("one", new String(l2.getByteContent("f1").getBytes()));
    assertEquals("two", new String(l3.getByteContent("f1").getBytes()));

    assertTrue(l3.getByteContent("dir").isDirectory());
    assertNull(l3.getByteContent("dir").getBytes());
  }
  
  @Test
  public void testGettingByteContentInsideChangeSet() throws Exception {
    vcs.beginChangeSet();
    vcs.createFile("f1", cf("one"), -1, false);
    Label l1 = vcs.putSystemLabel("label", -1);
    vcs.changeFileContent("f1", cf("two"), -1);
    Label l2 = vcs.putSystemLabel("label", -1);
    vcs.endChangeSet(null);

    assertEquals("one", new String(l1.getByteContent("f1").getBytes()));
    assertEquals("two", new String(l2.getByteContent("f1").getBytes()));
  }
}