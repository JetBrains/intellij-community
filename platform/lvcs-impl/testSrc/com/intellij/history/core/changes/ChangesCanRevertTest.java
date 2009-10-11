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

import com.intellij.history.core.LocalVcsTestCase;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.RootEntry;
import org.junit.Test;

public class ChangesCanRevertTest extends LocalVcsTestCase {
  Entry r = new RootEntry();

  @Test
  public void testRenameChange() {
    createFile(r, "f", null, -1);
    Change c = new RenameChange("f", "ff");

    c.applyTo(r);
    assertTrue(c.canRevertOn(r));

    createFile(r, "f", null, -1);
    assertFalse(c.canRevertOn(r));
  }

  @Test
  public void testMoveChange() {
    createDirectory(r, "dir1");
    createDirectory(r, "dir2");
    createFile(r, "dir1/f", null, -1);

    Change c = new MoveChange("dir1/f", "dir2");
    c.applyTo(r);
    assertTrue(c.canRevertOn(r));

    createFile(r, "dir1/f", null, -1);

    assertFalse(c.canRevertOn(r));
  }

  @Test
  public void testDeleteChange() {
    createFile(r, "f", null, -1);
    Change c = new DeleteChange("f");

    c.applyTo(r);
    assertTrue(c.canRevertOn(r));

    createFile(r, "f", null, -1);
    assertFalse(c.canRevertOn(r));
  }
}
