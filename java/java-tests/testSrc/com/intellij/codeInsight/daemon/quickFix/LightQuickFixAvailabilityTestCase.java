package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.intention.IntentionAction;

/**
 * tests corresponding intention for availability only, does not invoke action
 * @author cdr
 */
public abstract class LightQuickFixAvailabilityTestCase extends LightQuickFixTestCase {
  protected void doAction(final String text, final boolean actionShouldBeAvailable, final String testFullPath, final String testName)
    throws Exception {
    IntentionAction action = findActionWithText(text);
    assertTrue("Action with text '" + text + "' is " + (action == null ? "not " :"") +
               "available in test " + testFullPath,
      (action != null) == actionShouldBeAvailable);
  }
}
