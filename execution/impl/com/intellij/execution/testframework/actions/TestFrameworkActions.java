/*
 * User: anna
 * Date: 25-May-2007
 */
package com.intellij.execution.testframework.actions;

import com.intellij.execution.testframework.Filter;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestFrameworkPropertyListener;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.util.config.AbstractProperty;

public class TestFrameworkActions {
  public static void installFilterAction(final TestFrameworkRunningModel model) {
    final TestConsoleProperties properties = model.getProperties();
    final TestFrameworkPropertyListener<Boolean> hidePropertyListener = new TestFrameworkPropertyListener<Boolean>() {
        public void onChanged(final Boolean value) {
          final boolean shouldFilter = TestConsoleProperties.HIDE_PASSED_TESTS.value(properties);
          model.setFilter(shouldFilter ? Filter.NOT_PASSED : Filter.NO_FILTER);
        }
      };
    addPropertyListener(TestConsoleProperties.HIDE_PASSED_TESTS, hidePropertyListener, model, true);
  }

  public static void addPropertyListener(final AbstractProperty<Boolean> property,
                                         final TestFrameworkPropertyListener<Boolean> propertyListener,
                                         final TestFrameworkRunningModel model,
                                         final boolean sendValue) {
    final TestConsoleProperties properties = model.getProperties();
    if (sendValue) {
      properties.addListenerAndSendValue(property, propertyListener);
    }
    else {
      properties.addListener(property, propertyListener);
    }
    model.addListener(new TestFrameworkRunningModel.ModelListener() {
      public void onDispose(final TestFrameworkRunningModel model) {
        properties.removeListener(property, propertyListener);
      }
    });
  }
}