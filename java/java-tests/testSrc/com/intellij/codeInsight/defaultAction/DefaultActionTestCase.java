
package com.intellij.codeInsight.defaultAction;

import com.intellij.ide.DataManager;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.testFramework.LightCodeInsightTestCase;

public abstract class DefaultActionTestCase extends LightCodeInsightTestCase {
  protected void performAction(char c) {
    EditorActionManager actionManager = EditorActionManager.getInstance();
    TypedAction action = actionManager.getTypedAction();
    action.actionPerformed(getEditor(), c, DataManager.getInstance().getDataContext());
  }
}