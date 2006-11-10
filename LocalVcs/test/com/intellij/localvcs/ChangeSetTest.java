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
    changeSet = cs(new CreateFileChange(null, p("file1"), null),
                   new CreateFileChange(null, p("file2"), null),
                   new CreateFileChange(null, p("file3"), null));

    log = new ArrayList<Path>();
    root = new RootEntry() {
      @Override
      protected void doCreateFile(Integer id, Path path, String content) {
        super.doCreateFile(id, path, content);
        log.add(path);
      }

      @Override
      protected void doDelete(Path path) {
        super.doDelete(path);
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
    changeSet.applyTo(root);
    log.clear();

    changeSet.revertOn(root);
    assertElements(new Object[]{p("file3"), p("file2"), p("file1")}, log);
  }
}
