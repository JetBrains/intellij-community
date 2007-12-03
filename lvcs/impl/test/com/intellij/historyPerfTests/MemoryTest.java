package com.intellij.historyPerfTests;

import com.intellij.history.utils.RunnableAdapter;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class MemoryTest extends LocalVcsPerformanceTestCase {
  @Test
  public void testMemoryAfterFirstBuild() {
    assertMemoryUsage(52, new RunnableAdapter() {
      public void doRun() throws Exception {
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

    assertMemoryUsage(41, new RunnableAdapter() {
      public void doRun() throws Exception {
        initVcs();
      }
    });
  }
}