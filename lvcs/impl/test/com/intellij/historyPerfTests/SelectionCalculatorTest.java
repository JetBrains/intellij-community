package com.intellij.historyPerfTests;

import com.intellij.idea.Bombed;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.integration.ui.models.NullProgress;
import com.intellij.history.integration.ui.models.SelectionCalculator;
import com.intellij.history.utils.RunnableAdapter;
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
    assertExecutionTime(16000, new RunnableAdapter() {
      public void doRun() throws Exception {
        c.getSelectionFor(rr.get(100), new NullProgress());
      }
    });
  }

  @Test
  public void testUsingCache() {
    c.getSelectionFor(rr.get(100), new NullProgress());

    assertExecutionTime(1, new RunnableAdapter() {
      public void doRun() throws Exception {
        c.getSelectionFor(rr.get(1), new NullProgress());
        c.getSelectionFor(rr.get(50), new NullProgress());
        c.getSelectionFor(rr.get(100), new NullProgress());
      }
    });
  }
}
