package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.intention.IntentionAction;
import org.jetbrains.annotations.NotNull;

/**
 * tests corresponding intention for availability only, does not invoke action
 * @author cdr
 */
public abstract class LightQuickFixAvailabilityTestCase extends LightQuickFixParameterizedTestCase {
  @Override
  protected void doAction(@NotNull final String text, final boolean actionShouldBeAvailable, final String testFullPath, final String testName)
    throws Exception {
    IntentionAction action = findActionWithText(text);
    assertTrue("Action with text '" + text + "' is " + (action == null ? "not " :"") +
               "available in test " + testFullPath,
      (action != null) == actionShouldBeAvailable);
  }
}
