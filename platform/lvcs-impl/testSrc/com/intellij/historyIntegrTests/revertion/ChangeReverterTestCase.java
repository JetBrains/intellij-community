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

package com.intellij.historyIntegrTests.revertion;

import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.changes.Change;
import com.intellij.history.integration.revertion.ChangeReverter;
import com.intellij.history.integration.revertion.Reverter;
import com.intellij.historyIntegrTests.IntegrationTestCase;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.List;

public abstract class ChangeReverterTestCase extends IntegrationTestCase {
  protected void revertLastChange(VirtualFile f) throws IOException {
    revertChange(f, 0);
  }

  protected void revertChange(VirtualFile f, int index) throws IOException {
    createReverter(f, index).revert();
  }

  protected ChangeReverter createReverter(VirtualFile f, int index) {
    List<Revision> rr = getVcsRevisionsFor(f);
    return createReverter(rr.get(index).getCauseChange());
  }

  protected ChangeReverter createReverter(Change c) {
    return new ChangeReverter(getVcs(), gateway, c);
  }

  protected void assertCanRevert(VirtualFile f, int changeIndex) throws IOException {
    Reverter r = createReverter(f, changeIndex);
    assertTrue(r.checkCanRevert().isEmpty());
  }

  protected void assertCanNotRevert(VirtualFile f, int changeIndex, String error) throws IOException {
    List<String> errors = getCanRevertErrors(f, changeIndex);

    assertEquals(1, errors.size());
    assertEquals(error, errors.get(0));
  }

  protected List<String> getCanRevertErrors(VirtualFile f, int changeIndex) throws IOException {
    Reverter r = createReverter(f, changeIndex);
    return r.checkCanRevert();
  }
}
