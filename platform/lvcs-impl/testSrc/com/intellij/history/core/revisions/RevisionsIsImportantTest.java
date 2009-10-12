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

import com.intellij.history.core.LocalVcsTestCase;
import com.intellij.history.core.changes.Change;
import com.intellij.history.core.changes.PutLabelChange;
import com.intellij.history.core.changes.PutSystemLabelChange;
import org.junit.Test;

public class RevisionsIsImportantTest extends LocalVcsTestCase {
  @Test
  public void testCurrentRevision() {
    Revision r = new CurrentRevision(null);
    assertTrue(r.isImportant());
  }

  @Test
  public void testLabeledRevision() {
    Revision r1 = createLabeledRevision(new PutLabelChange(null, -1));
    Revision r2 = createLabeledRevision(new PutSystemLabelChange(null, -1, -1));
    assertTrue(r1.isImportant());
    assertFalse(r2.isImportant());
  }

  private Revision createLabeledRevision(Change c) {
    return new LabeledRevision(null, null, null, c);
  }

  @Test
  public void testRevisionBeforeChange() {
    Revision r = new RevisionBeforeChange(null, null, null, null);
    assertTrue(r.isImportant());
  }

  @Test
  public void testRevisionAfterChange() {
    Revision r = new RevisionBeforeChange(null, null, null, null);
    assertTrue(r.isImportant());
  }
}
