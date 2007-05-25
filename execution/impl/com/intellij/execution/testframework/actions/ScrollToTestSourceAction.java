package com.intellij.execution.testframework.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.config.AbstractProperty;
import com.intellij.util.config.ToggleBooleanProperty;

public class ScrollToTestSourceAction extends ToggleBooleanProperty.Disablable {
  private TestFrameworkRunningModel myModel;
  public ScrollToTestSourceAction(final TestConsoleProperties properties) {
    super(ExecutionBundle.message("junit.auto.scroll.to.source.action.name"),
          ExecutionBundle.message("junit.open.text.in.editor.action.name"),
          IconLoader.getIcon("/general/autoscrollToSource.png"),
          properties, TestConsoleProperties.SCROLL_TO_SOURCE);
  }

  protected boolean isEnabled() {
    final AbstractProperty.AbstractPropertyContainer properties = getProperties();
    final TestFrameworkRunningModel model = myModel;
    return isEnabled(properties, model);
  }

  private static boolean isEnabled(final AbstractProperty.AbstractPropertyContainer properties, final TestFrameworkRunningModel model) {
    if (!TestConsoleProperties.TRACK_RUNNING_TEST.value(properties)) return true;
    return model != null && !model.isRunning();
  }

  public static boolean isScrollEnabled(final TestFrameworkRunningModel model) {
    final TestConsoleProperties properties = model.getProperties();
    return isEnabled(properties, model) && TestConsoleProperties.SCROLL_TO_SOURCE.value(properties);
  }

  public void setModel(final TestFrameworkRunningModel model) {
    myModel = model;
  }
}
