package com.intellij.execution.actions;

import com.intellij.execution.Executor;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.ui.popup.WizardPopup;
import com.intellij.ui.popup.list.ListPopupImpl;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.List;

class RunListPopup extends ListPopupImpl {
  private ChooseRunConfigurationPopup myChooseRunConfigurationPopup;
  private Executor myAlternativeExecutor;
  private Project myProject;

  RunListPopup(ChooseRunConfigurationPopup chooseRunConfigurationPopup,
                       Executor alternativeExecutor,
                       Project project,
                       ListPopupStep step) {
    super(step);
    myChooseRunConfigurationPopup.registerActions(this);
    this.myChooseRunConfigurationPopup = chooseRunConfigurationPopup;
    this.myAlternativeExecutor = alternativeExecutor;
    this.myProject = project;
  }

  protected RunListPopup(ChooseRunConfigurationPopup chooseRunConfigurationPopup,
                         Executor alternativeExecutor, Project project,
                         WizardPopup aParent,
                         ListPopupStep aStep,
                         Object parentValue) {
    super(aParent, aStep, parentValue);
    myChooseRunConfigurationPopup.registerActions(this);
    this.myChooseRunConfigurationPopup = chooseRunConfigurationPopup;
    this.myAlternativeExecutor = alternativeExecutor;
    this.myProject = project;
  }

  @Override
  protected WizardPopup createPopup(WizardPopup parent, PopupStep step, Object parentValue) {
    return new RunListPopup(myChooseRunConfigurationPopup, myAlternativeExecutor, myProject, parent, (ListPopupStep)step, parentValue);
  }

  @Override
  public void handleSelect(boolean handleFinalChoices, InputEvent e) {
    if (e instanceof MouseEvent && e.isShiftDown()) {
      handleShiftClick(handleFinalChoices, e, this);
      return;
    }

    _handleSelect(handleFinalChoices, e);
  }

  private void _handleSelect(boolean handleFinalChoices, InputEvent e) {
    super.handleSelect(handleFinalChoices, e);
  }

  protected void handleShiftClick(boolean handleFinalChoices, final InputEvent inputEvent, final RunListPopup popup) {
    myChooseRunConfigurationPopup.myCurrentExecutor = myAlternativeExecutor;
    popup._handleSelect(handleFinalChoices, inputEvent);
  }

  @Override
  protected ListCellRenderer getListElementRenderer() {
    boolean hasSideBar = false;
    for (Object each : getListStep().getValues()) {
      if (each instanceof ChooseRunConfigurationPopup.Wrapper) {
        if (((ChooseRunConfigurationPopup.Wrapper)each).getMnemonic() != -1) {
          hasSideBar = true;
          break;
        }
      }
    }
    return new RunListElementRenderer(this, hasSideBar);
  }

  public void removeSelected() {
    final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
    if (!propertiesComponent.isTrueValue("run.configuration.delete.ad")) {
      propertiesComponent.setValue("run.configuration.delete.ad", Boolean.toString(true));
    }

    final int index = getSelectedIndex();
    if (index == -1) {
      return;
    }

    final Object o = getListModel().get(index);
    if (o != null && o instanceof ChooseRunConfigurationPopup.ItemWrapper && ((ChooseRunConfigurationPopup.ItemWrapper)o).canBeDeleted()) {
      final RunManagerEx manager = RunManagerEx.getInstanceEx(myProject);
      manager.removeConfiguration((RunnerAndConfigurationSettings)((ChooseRunConfigurationPopup.ItemWrapper)o).getValue());
      getListModel().deleteItem(o);
      final List<Object> values = getListStep().getValues();
      values.remove(o);

      if (index < values.size()) {
        onChildSelectedFor(values.get(index));
      }
      else if (index - 1 >= 0) {
        onChildSelectedFor(values.get(index - 1));
      }
    }
  }
}
