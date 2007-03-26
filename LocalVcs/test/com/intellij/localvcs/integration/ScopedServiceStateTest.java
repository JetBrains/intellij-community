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
    gw.addUnsavedDocument("file", "new", -1);

    initState();

    assertEquals(c("new"), vcs.getEntry("file").getContent());
    assertEquals(456L, vcs.getEntry("file").getTimestamp());

    assertEquals(2, vcs.getLabelsFor("file").size());
  }

  @Test
  @Ignore
  public void testRegisteringUnsavedDocumentsAsOneChangeSetBeforeEntering() {
    vcs.beginChangeSet();
    vcs.createDirectory("dir");
    vcs.createFile("dir/one", null, -1);
    vcs.createFile("dir/two", null, -1);
    vcs.endChangeSet(null);

    gw.addUnsavedDocument("dir/one", "one", -1);
    gw.addUnsavedDocument("dir/two", "two", -1);
    initState();

    assertEquals(2, vcs.getLabelsFor("dir").size());
  }

  @Test
  @Ignore
  public void testRegisteringUnsavedDocumentsBeforeEnteringSeparately() {
    vcs.createFile("f", b("one"), -1);

    gw.addUnsavedDocument("f", "two", -1);
    initState();
    vcs.changeFileContent("f", b("three"), -1);
    s.goToState(null);

    assertEquals(3, vcs.getLabelsFor("f").size());
  }

  @Test
  @Ignore
  public void testRegisteringUnsavedDocumentsBeforeExitingState() {
    vcs.createFile("file", b("old"), 123L);
    initState();

    Clock.setCurrentTimestamp(789);
    gw.addUnsavedDocument("file", "new", -1);

    s.goToState(null);

    assertEquals(c("new"), vcs.getEntry("file").getContent());
    assertEquals(789L, vcs.getEntry("file").getTimestamp());

    assertEquals(2, vcs.getLabelsFor("file").size());
  }

  @Test
  @Ignore
  public void testRegisteringUnsavedDocumentsBeforeExitingStateWithinInnerChangeset() {
    vcs.beginChangeSet();
    vcs.createDirectory("dir");
    vcs.createFile("dir/one", null, -1);
    vcs.createFile("dir/two", null, -1);
    vcs.endChangeSet(null);

    initState();
    vcs.createFile("dir/three", null, -1);

    gw.addUnsavedDocument("dir/one", "one", -1);
    gw.addUnsavedDocument("dir/two", "two", -1);
    s.goToState(null);

    assertEquals(2, vcs.getLabelsFor("dir").size());
  }

  @Test
  @Ignore
  public void testFilteringDocuments() {
    TestFileFilter ff = new TestFileFilter();
    gw.setFileFilter(ff);

    vcs.createFile("f", b("old"), -1);

    TestVirtualFile f = new TestVirtualFile("f", "new", -1);
    ff.setFilesNotUnderContentRoot(f);

    initState();

    assertEquals(c("old"), vcs.getEntry("f").getContent());
  }

  private void initState() {
    s = new ScopedServiceState("name", new ServiceStateHolder(), vcs, gw) {
    };
  }
}
