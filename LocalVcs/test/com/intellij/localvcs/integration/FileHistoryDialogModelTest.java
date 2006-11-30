package com.intellij.localvcs.integration;

import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.TestStorage;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

import java.util.List;

public class FileHistoryDialogModelTest {
  private LocalVcs vcs = new LocalVcs(new TestStorage());


  @Test
  public void testLabelsList() {
    vcs.createFile("file", null, null);
    vcs.apply();
    vcs.putLabel("1");

    vcs.changeFileContent("file", null, null);
    vcs.apply();
    vcs.putLabel("2");

    FileHistoryDialogModel m = new FileHistoryDialogModel(vcs, "file");
    List<String> l = m.getLabels();

    assertEquals(2, l.size());
    assertEquals("2", l.get(0));
    assertEquals("1", l.get(1));
  }

  //@Test
  //public void testGettingContentForLabels() {
  //  vcs.createFile("file", null, null);
  //  vcs.apply();
  //  vcs.putLabel("1");
  //
  //  vcs.changeFileContent("file", null, null);
  //  vcs.apply();
  //  vcs.putLabel("2");
  //
  //}

  @Test
  public void testIncludingCurrentUnsavedVersionInLabels() {
    // todo
    
  }
}
