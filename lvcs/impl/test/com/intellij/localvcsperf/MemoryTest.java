package com.intellij.localvcsperf;

import com.intellij.idea.Bombed;
import org.junit.Test;

import java.util.Calendar;

@Bombed(month = Calendar.MAY, day = 31, user = "anton")
public class MemoryTest extends LocalVcsPerformanceTestCase {
  @Test
  public void testMemoryAfterFirstBuild() {
    assertMemoryUsage(53, new Task() {
      public void execute() throws Exception {
        buildVcsTree();
      }
    });
  }

  @Test
  public void testMemoryAfterLoad() {
    buildVcsTree();

    vcs.save();
    closeStorage();
    vcs = null;

    assertMemoryUsage(42, new Task() {
      public void execute() throws Exception {
        initVcs();
      }
    });
  }
}