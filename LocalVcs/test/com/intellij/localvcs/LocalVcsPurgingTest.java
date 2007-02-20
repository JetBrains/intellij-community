package com.intellij.localvcs;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class LocalVcsPurgingTest extends LocalVcsTestCase {
  TestLocalVcs vcs = new TestLocalVcs(new PurgingLoggingStorage());
  List<Content> purgedContent;

  @Before
  public void setUp() {
    vcs.setCurrentTimestamp(10);
    vcs.createFile("file", b("content"), null);
    vcs.apply();

    vcs.setCurrentTimestamp(20);
    vcs.changeFileContent("file", b("new content"), null);
    vcs.apply();
  }

  @Test
  public void testPurging() {
    vcs.purgeUpTo(15);

    List<Label> labels = vcs.getLabelsFor("file");
    assertEquals(1, labels.size());
    assertEquals(20L, labels.get(0).getTimestamp());
  }

  @Test
  public void testPurgingContents() {
    vcs.purgeUpTo(30);

    assertEquals(1, purgedContent.size());
    assertEquals(c("content"), purgedContent.get(0));
  }

  @Test
  public void testPurgingOnSave() {
    vcs.setPurgingInterval(30);

    assertLabelsCountAfterPurgeOnSave(40, 2);
    assertLabelsCountAfterPurgeOnSave(41, 1);
  }

  @Test
  public void testDefaultPurgingInterval() {
    LocalVcs vcs = new LocalVcs(new TestStorage());
    assertEquals(5 * 24 * 60 * 60 * 1000L, vcs.getPurgingInterval());
  }

  private void assertLabelsCountAfterPurgeOnSave(long timestamp, int count) {
    vcs.setCurrentTimestamp(timestamp);
    vcs.save();
    assertEquals(count, vcs.getLabelsFor("file").size());
  }

  class PurgingLoggingStorage extends TestStorage {
    @Override
    public void purgeContents(List<Content> cc) {
      purgedContent = cc;
    }
  }
}