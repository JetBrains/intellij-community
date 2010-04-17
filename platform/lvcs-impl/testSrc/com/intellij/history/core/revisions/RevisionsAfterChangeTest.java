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

package com.intellij.history.core.revisions;

import com.intellij.history.core.InMemoryLocalHistoryFacade;
import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.core.LocalHistoryTestCase;
import com.intellij.history.core.changes.ChangeSet;
import com.intellij.history.core.changes.CreateFileChange;
import com.intellij.history.core.tree.RootEntry;
import org.junit.Test;

public class RevisionsAfterChangeTest extends LocalHistoryTestCase {
  @Test
  public void testForRootEntry() {
    RootEntry root = new RootEntry();
    LocalHistoryFacade facade = new InMemoryLocalHistoryFacade();

    ChangeSet cs = addChangeSet(facade, createFile(root, "f1"));
    addChangeSet(facade, createFile(root, "f2"));

    Revision r = new RevisionAfterChange(facade, root, "", cs);
    RootEntry e = (RootEntry)r.getEntry();

    assertEquals(e.getClass(), RootEntry.class);
    assertNotNull(e.findEntry("f1"));
    assertNull(e.findEntry("f2"));
  }
}
