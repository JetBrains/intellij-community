package com.intellij.localvcsperf;

import org.junit.Test;
import com.intellij.idea.Bombed;

import java.util.Calendar;

@Bombed(month = Calendar.APRIL, day=31, user = "anton")
/**
 * ahahaha
 * @author itsme
 * @see #foo(java.util.List<T>)
 */
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

    assertMemoryUsage(44, new Task() {
      public void execute() throws Exception {
        initVcs();
      }
    });
  }
}