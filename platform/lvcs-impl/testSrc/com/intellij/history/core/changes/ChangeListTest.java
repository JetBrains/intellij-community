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

package com.intellij.history.core.changes;

import com.intellij.history.core.tree.Entry;
import org.junit.Test;

import java.util.List;

public class ChangeListTest extends ChangeListTestCase {
  @Test
  public void testRevertionUpToInclusively() {
    applyAndAdd(cs("1", new CreateFileChange(1, "file1", null, -1, false)));
    applyAndAdd(cs("2", new CreateFileChange(2, "file2", null, -1, false)));

    Entry copy = r.copy();
    cl.revertUpTo(copy, cl.getChanges().get(0), true);
    assertTrue(copy.hasEntry("file1"));
    assertFalse(copy.hasEntry("file2"));

    copy = r.copy();
    cl.revertUpTo(copy, cl.getChanges().get(1), true);
    assertFalse(copy.hasEntry("file1"));
    assertFalse(copy.hasEntry("file2"));
  }

  @Test
  public void testRevertionUpToExclusively() {
    applyAndAdd(cs("1", new CreateFileChange(1, "file1", null, -1, false)));
    applyAndAdd(cs("2", new CreateFileChange(2, "file2", null, -1, false)));

    Entry copy = r.copy();
    cl.revertUpTo(copy, cl.getChanges().get(0), false);
    assertTrue(copy.hasEntry("file1"));
    assertTrue(copy.hasEntry("file2"));

    copy = r.copy();
    cl.revertUpTo(copy, cl.getChanges().get(1), false);
    assertTrue(copy.hasEntry("file1"));
    assertFalse(copy.hasEntry("file2"));
  }

  @Test
  public void testChangeSet() {
    Change c1 = new CreateFileChange(1, "f1", null, -1, false);
    Change c2 = new CreateFileChange(2, "f2", null, -1, false);

    cl.beginChangeSet();
    cl.addChange(c1);
    cl.addChange(c2);
    cl.endChangeSet("changeSet");

    List<Change> cc = cl.getChanges();
    assertEquals(1, cc.size());
    assertEquals("changeSet", cc.get(0).getName());
    assertEquals(ChangeSet.class, cc.get(0).getClass());
    assertEquals(array(c1, c2), cc.get(0).getChanges());
  }

  @Test
  public void testChangeSetTimestamp() {
    setCurrentTimestamp(123);
    cl.beginChangeSet();
    cl.addChange(new CreateFileChange(1, "f", null, -1, false));
    setCurrentTimestamp(456);
    cl.endChangeSet(null);

    assertEquals(123, cl.getChanges().get(0).getTimestamp());
  }

  @Test
  public void testSkippingEmptyChangeSets() {
    cl.beginChangeSet();
    cl.endChangeSet(null);
    assertTrue(cl.getChanges().isEmpty());
  }

  @Test
  public void testSkippingInnerChangeSets() {
    Change c1 = new CreateFileChange(1, "f1", null, -1, false);
    Change c2 = new CreateFileChange(2, "f2", null, -1, false);

    cl.beginChangeSet();
    cl.addChange(c1);
    cl.beginChangeSet();
    cl.addChange(c2);
    cl.endChangeSet("inner");
    cl.endChangeSet("outer");

    List<Change> cc = cl.getChanges();
    assertEquals(1, cc.size());
    assertEquals("outer", cc.get(0).getName());
    assertEquals(ChangeSet.class, cc.get(0).getClass());
    assertEquals(array(c1, c2), cc.get(0).getChanges());
  }
}
