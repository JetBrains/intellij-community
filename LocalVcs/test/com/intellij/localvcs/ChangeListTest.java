package com.intellij.localvcs;

import java.io.File;
import java.net.URISyntaxException;

import com.intellij.openapi.util.io.FileUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ChangeListTest extends TestCase {
  private File myTempDir;

  @Before
  public void createTempDir() {
    try {
      File root = new File(getClass().getResource(".").toURI());
      myTempDir = new File(root, "temp");
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }

    deleteTempDir();
    myTempDir.mkdirs();
  }

  @After
  public void deleteTempDir() {
    if (!FileUtil.delete(myTempDir))
      throw new RuntimeException("can't delete temp dir");
  }

  @Test
  public void testHasOneChangeSetByDefault() {
    ChangeList l = new ChangeList();
    assertTrue(l.hasOnlyOneChangeSet());
  }

  @Test
  public void testSavingEmpty() {
    ChangeList l = new ChangeList();
    assertTrue(myTempDir.listFiles().length == 0);

    l.save(myTempDir);
    assertTrue(myTempDir.listFiles().length != 0);

    ChangeList result = new ChangeList(myTempDir);
    assertTrue(result.hasOnlyOneChangeSet());
  }

  @Test
  public void testSavingWithChangeSets() {
    ChangeList l = new ChangeList();
    l.add(cs(new CreateFileChange(p("dir/file"), "content")));
    l.save(myTempDir);

    ChangeList result = new ChangeList(myTempDir);
    //assertEquals(2, result.getChangeSets().size());
    //assertEquals(CreateFileChange.class, result.getChangeSets().get(1));
  }
}
