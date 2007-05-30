package com.intellij.localvcsperf;

import com.intellij.idea.Bombed;
import com.intellij.localvcs.core.revisions.Revision;
import com.intellij.localvcs.integration.ui.models.SelectionCalculator;
import org.junit.Before;
import org.junit.Test;

import java.util.Calendar;
import java.util.List;

@Bombed(month = Calendar.AUGUST, day = 31, user = "anton")
public class SelectionCalculatorTest extends LocalVcsPerformanceTestCase {
  private List<Revision> rr;
  private SelectionCalculator c;

  @Before
  public void setUp() {
    buildVcsTree();
    for (int i = 0; i < 100; i++) {
      vcs.changeFileContent("root/file1", cf("content" + i), -1);
    }
    rr = vcs.getRevisionsFor("root/file1");
    c = new SelectionCalculator(rr, 0, 0);
  }

  @Test
  public void testCalculationFromTheScratch() {
    assertExecutionTime(1, new Task() {
      public void execute() throws Exception {
        c.getSelectionFor(rr.get(100));
      }
    });
  }

  @Test
  public void testUsingCache() {
    c.getSelectionFor(rr.get(100));

    assertExecutionTime(1, new Task() {
      public void execute() throws Exception {
        c.getSelectionFor(rr.get(1));
        c.getSelectionFor(rr.get(50));
        c.getSelectionFor(rr.get(100));
      }
    });
  }
}
