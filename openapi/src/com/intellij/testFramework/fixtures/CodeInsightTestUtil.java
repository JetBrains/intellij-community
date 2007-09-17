/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package com.intellij.testFramework.fixtures;

import com.intellij.codeInsight.intention.IntentionAction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class CodeInsightTestUtil {
  
  private CodeInsightTestUtil() {
  }

  @Nullable
  public static IntentionAction findIntentionByText(List<IntentionAction> actions, @NonNls String text) {
    for (IntentionAction action : actions) {
      final String s = action.getText();
      if (s.equals(text)) {
        return action;
      }
    }
    return null;
  }

  public static void doIntentionTest(CodeInsightTestFixture fixture, @NonNls String file, @NonNls String actionText) throws Throwable {
    final List<IntentionAction> list = fixture.getAvailableIntentions(file + ".xml");
    assert list.size() > 0;
    final IntentionAction intentionAction = findIntentionByText(list, actionText);
    assert intentionAction != null;
    fixture.launchAction(intentionAction);
    fixture.checkResultByFile(file + "_after.xml");
  }
}
