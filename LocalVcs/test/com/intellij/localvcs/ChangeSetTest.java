package com.intellij.localvcs;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class ChangeSetTest extends TestCase {
  private ChangeSet changeSet;
  private List<Path> log;
  private RootEntry root;

  @Before
  public void setUp() {
    changeSet = cs(new CreateFileChange(p("file1"), null, null),
                   new CreateFileChange(p("file2"), null, null),
                   new CreateFileChange(p("file3"), null, null));

    log = new ArrayList<Path>();
    root = new RootEntry() {
      @Override
      protected void doCreateFile(Path path, String content, Integer id) {
        log.add(path);
      }

      @Override
      protected void doDelete(Path path) {
        log.add(path);
      }
    };
  }

  @Test
  public void testApplyingIsFIFO() {
    changeSet.applyTo(root);
    assertElements(new Object[]{p("file1"), p("file2"), p("file3")}, log);
  }

  @Test
  public void testRevertingIsLIFO() {
    changeSet.revertOn(root);
    assertElements(new Object[]{p("file3"), p("file2"), p("file1")}, log);
  }
}
