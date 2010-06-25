package com.intellij.codeInsight.intention;

import com.intellij.codeInsight.daemon.LightIntentionActionTestCase;

/**
 * @author ven
 */
public class MoveFieldAssignmentToInitializerActionTest extends LightIntentionActionTestCase {
  public void test() throws Exception { doAllTests(); }

  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/moveFieldAssignmentToInitializer";
  }
}
