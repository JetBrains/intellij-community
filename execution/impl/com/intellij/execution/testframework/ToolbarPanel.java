/*
 * User: anna
 * Date: 25-May-2007
 */
package com.intellij.execution.testframework;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.junit2.ui.TestsUIUtil;
import com.intellij.execution.junit2.ui.properties.JUnitConsoleProperties;
import com.intellij.execution.testframework.actions.ScrollToTestSourceAction;
import com.intellij.execution.testframework.actions.TestFrameworkActions;
import com.intellij.execution.testframework.actions.TestTreeExpander;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.OccurenceNavigator;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.config.ToggleBooleanProperty;

import javax.swing.*;
import java.awt.*;

public abstract class ToolbarPanel extends JPanel implements OccurenceNavigator {
  protected final TestTreeExpander myTreeExpander = new TestTreeExpander();
  protected final FailedTestsNavigator myOccurenceNavigator;
  protected final ScrollToTestSourceAction myScrollToSource;

  public ToolbarPanel(final TestConsoleProperties properties,
                      final RunnerSettings runnerSettings,
                      final ConfigurationPerRunnerSettings configurationSettings) {
    super (new BorderLayout());
    add(new JLabel(IconLoader.getIcon("/general/inactiveSeparator.png")), BorderLayout.WEST);
    final DefaultActionGroup actionGroup = new DefaultActionGroup(null, false);
    actionGroup.addSeparator();
    actionGroup.add(new ToggleBooleanProperty(ExecutionBundle.message("junit.run.hide.passed.action.name"),
                                              ExecutionBundle.message("junit.run.hide.passed.action.description"),
                                              TestsUIUtil.loadIcon("hidePassed"),
                                              properties, JUnitConsoleProperties.HIDE_PASSED_TESTS));
    actionGroup.add(new ToggleBooleanProperty(ExecutionBundle.message("junit.runing.info.track.test.action.name"),
                                              ExecutionBundle.message("junit.runing.info.track.test.action.description"),
                                              TestsUIUtil.loadIcon("trackTests"),
                                              properties, JUnitConsoleProperties.TRACK_RUNNING_TEST));
    actionGroup.addSeparator();
    AnAction action = CommonActionsManager.getInstance().createCollapseAllAction(myTreeExpander, this);
    action.getTemplatePresentation().setDescription(ExecutionBundle.message("junit.runing.info.collapse.test.action.name"));
    actionGroup.add(action);

    action = CommonActionsManager.getInstance().createExpandAllAction(myTreeExpander, this);
    action.getTemplatePresentation().setDescription(ExecutionBundle.message("junit.runing.info.expand.test.action.name"));
    actionGroup.add(action);

    actionGroup.addSeparator();
    final CommonActionsManager actionsManager = CommonActionsManager.getInstance();
    myOccurenceNavigator = getOccurenceNavigator(properties);
    actionGroup.add(actionsManager.createPrevOccurenceAction(myOccurenceNavigator));
    actionGroup.add(actionsManager.createNextOccurenceAction(myOccurenceNavigator));
    actionGroup.addSeparator();
    actionGroup.add(new ToggleBooleanProperty(ExecutionBundle.message("junit.runing.info.select.first.failed.action.name"),
                                              null,
                                              TestsUIUtil.loadIcon("selectFirstDefect"),
                                              properties, JUnitConsoleProperties.SELECT_FIRST_DEFECT));
    actionGroup.add(new ToggleBooleanProperty(ExecutionBundle.message("junit.runing.info.scroll.to.stacktrace.action.name"),
                                              ExecutionBundle.message("junit.runing.info.scroll.to.stacktrace.action.description"),
                                              IconLoader.getIcon("/runConfigurations/scrollToStackTrace.png"),
                                              properties, JUnitConsoleProperties.SCROLL_TO_STACK_TRACE));
    myScrollToSource = new ScrollToTestSourceAction(properties);
    actionGroup.add(myScrollToSource);
    actionGroup.add(new ToggleBooleanProperty(ExecutionBundle.message("junit.runing.info.open.source.at.exception.action.name"),
                                              ExecutionBundle.message("junit.runing.info.open.source.at.exception.action.description"),
                                              IconLoader.getIcon("/runConfigurations/sourceAtException.png"),
                                              properties, JUnitConsoleProperties.OPEN_FAILURE_LINE));
    appendAdditionalActions(actionGroup, properties, runnerSettings, configurationSettings);

    add(ActionManager.getInstance().
        createActionToolbar(ActionPlaces.TESTTREE_VIEW_TOOLBAR, actionGroup, true).
        getComponent(), BorderLayout.CENTER);
  }

  protected abstract FailedTestsNavigator getOccurenceNavigator(TestConsoleProperties properties);

  protected abstract void appendAdditionalActions(DefaultActionGroup actionGroup, TestConsoleProperties properties, RunnerSettings runnerSettings,
                                         ConfigurationPerRunnerSettings configurationSettings);

  public void setModel(final TestFrameworkRunningModel model) {
    TestFrameworkActions.installFilterAction(model);
    myScrollToSource.setModel(model);
    myTreeExpander.setModel(model);
    myOccurenceNavigator.setModel(model);
  }

  public boolean hasNextOccurence() {
    return myOccurenceNavigator.hasNextOccurence();
  }

  public boolean hasPreviousOccurence() {
    return myOccurenceNavigator.hasPreviousOccurence();
  }

  public OccurenceInfo goNextOccurence() {
    return myOccurenceNavigator.goNextOccurence();
  }

  public OccurenceInfo goPreviousOccurence() {
    return myOccurenceNavigator.goPreviousOccurence();
  }

  public String getNextOccurenceActionName() {
    return myOccurenceNavigator.getNextOccurenceActionName();
  }

  public String getPreviousOccurenceActionName() {
    return myOccurenceNavigator.getPreviousOccurenceActionName();
  }

  public void dispose() {
    myScrollToSource.setModel(null);    
  }
}