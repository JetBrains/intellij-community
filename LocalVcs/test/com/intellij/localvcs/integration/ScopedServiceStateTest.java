package com.intellij.localvcs.integration;

import com.intellij.localvcs.Clock;
import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.LocalVcsTestCase;
import com.intellij.localvcs.TestLocalVcs;
import org.junit.Ignore;
import org.junit.Test;

public class ScopedServiceStateTest extends LocalVcsTestCase {
  LocalVcs vcs = new TestLocalVcs();
  TestIdeaGateway gw = new TestIdeaGateway();
  ScopedServiceState s;

  @Test
  @Ignore
  public void testRegisteringUnsavedDocumentsBeforeEnteringState() {
    vcs.createFile("file", b("old"), 123L);

    Clock.setCurrentTimestamp(456);
    gw.addUnsavedDocument("file", "new", null);

    initState();

    assertEquals(c("new"), vcs.getEntry("file").getContent());
    assertEquals(456L, vcs.getEntry("file").getTimestamp());

    assertEquals(2, vcs.getLabelsFor("file").size());
  }

  @Test
  @Ignore
  public void testRegisteringUnsavedDocumentsAsOneChangeSetBeforeEntering() {
    vcs.beginChangeSet();
    vcs.createDirectory("dir", null);
    vcs.createFile("dir/one", null, null);
    vcs.createFile("dir/two", null, null);
    vcs.endChangeSet(null);

    gw.addUnsavedDocument("dir/one", "one", null);
    gw.addUnsavedDocument("dir/two", "two", null);
    initState();

    assertEquals(2, vcs.getLabelsFor("dir").size());
  }

  @Test
  @Ignore
  public void testRegisteringUnsavedDocumentsBeforeEnteringSeparately() {
    vcs.createFile("f", b("one"), null);

    gw.addUnsavedDocument("f", "two", null);
    initState();
    vcs.changeFileContent("f", b("three"), null);
    s.goToState(null);

    assertEquals(3, vcs.getLabelsFor("f").size());
  }

  @Test
  @Ignore
  public void testRegisteringUnsavedDocumentsBeforeExitingState() {
    vcs.createFile("file", b("old"), 123L);
    initState();

    Clock.setCurrentTimestamp(789);
    gw.addUnsavedDocument("file", "new", null);

    s.goToState(null);

    assertEquals(c("new"), vcs.getEntry("file").getContent());
    assertEquals(789L, vcs.getEntry("file").getTimestamp());

    assertEquals(2, vcs.getLabelsFor("file").size());
  }

  @Test
  @Ignore
  public void testRegisteringUnsavedDocumentsBeforeExitingStateWithinInnerChangeset() {
    vcs.beginChangeSet();
    vcs.createDirectory("dir", null);
    vcs.createFile("dir/one", null, null);
    vcs.createFile("dir/two", null, null);
    vcs.endChangeSet(null);

    initState();
    vcs.createFile("dir/three", null, null);

    gw.addUnsavedDocument("dir/one", "one", null);
    gw.addUnsavedDocument("dir/two", "two", null);
    s.goToState(null);

    assertEquals(2, vcs.getLabelsFor("dir").size());
  }

  @Test
  @Ignore
  public void testFilteringDocuments() {
    TestFileFilter ff = new TestFileFilter();
    gw.setFileFilter(ff);

    vcs.createFile("f", b("old"), null);

    TestVirtualFile f = new TestVirtualFile("f", "new", null);
    ff.setFilesNotUnderContentRoot(f);

    initState();

    assertEquals(c("old"), vcs.getEntry("f").getContent());
  }

  private void initState() {
    s = new ScopedServiceState("name", new ServiceStateHolder(), vcs, gw) {
    };
  }
}
