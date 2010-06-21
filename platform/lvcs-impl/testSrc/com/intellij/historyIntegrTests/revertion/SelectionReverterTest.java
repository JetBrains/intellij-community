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

import com.intellij.history.Clock;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.revertion.SelectionReverter;
import com.intellij.history.integration.ui.models.SelectionCalculator;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.historyIntegrTests.IntegrationTestCase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.List;
import java.util.Date;
import java.util.ArrayList;

public class SelectionReverterTest extends IntegrationTestCase {
  private VirtualFile f;

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();
    f = myRoot.createChildData(null, "f.txt");
  }

  public void testBasics() throws IOException {
    String before = "public class Bar {\n" +
                    "  public String foo() {\n" +
                    "    return \"old\";\n" +
                    "  }\n" +
                    "}\n";
    String after = "public class NewBar {\n" +
                   "  public String foo() {\n" +
                   "    return \"new\";\n" +
                   "  }\n" +
                   "  public abstract bar();\n" +
                   "}\n";

    f.setBinaryContent(before.getBytes());
    f.setBinaryContent(after.getBytes());

    revertToPreviousRevision(2, 2);
    
    String expected = "public class NewBar {\n" +
                      "  public String foo() {\n" +
                      "    return \"old\";\n" +
                      "  }\n" +
                      "  public abstract bar();\n" +
                      "}\n";
    assertEquals(expected, new String(f.contentsToByteArray()));
  }

  public void testChangeSetName() throws IOException {
    Clock.setCurrentTimestamp(new Date(2001, 1, 11, 12, 30).getTime());

    f.setBinaryContent("one".getBytes());
    f.setBinaryContent("two".getBytes());

    revertToPreviousRevision(0, 0);

    List<Revision> rr = getRevisionsFor(f);
    assertEquals(5, rr.size());
    assertEquals("Revert of selection to 11.02.01 12:30", rr.get(1).getChangeSetName());
  }

  public void testAskingForReadOnlyStatusClearingOnlyForTheSpecifiedFile() throws Exception {
    myRoot.createChildData(null, "foo1.txt");
    f.setBinaryContent("one".getBytes());
    myRoot.createChildData(null, "foo2.txt");
    f.setBinaryContent("two".getBytes());
    myRoot.createChildData(null, "foo3.txt");

    final List<VirtualFile> files = new ArrayList<VirtualFile>();
    myGateway = new IdeaGateway() {
      @Override
      public boolean ensureFilesAreWritable(Project p, List<VirtualFile> ff) {
        files.addAll(ff);
        return true;
      }
    };

    List<String> errors = checkCanRevertToPreviousRevision();
    assertTrue(errors.isEmpty());

    assertEquals(1, files.size());
    assertEquals(f, files.get(0));
  }

  private void revertToPreviousRevision(int from, int to) throws IOException {
    createReverter(from, to).revert();
  }

  private List<String> checkCanRevertToPreviousRevision() throws IOException {
    return createReverter(0, 0).checkCanRevert();
  }

  private SelectionReverter createReverter(int from, int to) {
    List<Revision> rr = getRevisionsFor(f);
    SelectionCalculator c = new SelectionCalculator(myGateway, rr, from, to);
    Revision leftRev = rr.get(1);
    Entry entry = getRootEntry().getEntry(f.getPath());

    return new SelectionReverter(myProject, getVcs(), myGateway, c, leftRev, entry, from, to);
  }
}
