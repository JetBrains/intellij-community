package com.intellij.openapi.options.newEditor;

import com.intellij.CommonBundle;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Map;

public class OptionsEditorDialog extends DialogWrapper {

  private Project myProject;
  private ConfigurableGroup[] myGroups;
  private Configurable myPreselected;
  private OptionsEditor myEditor;

  private ApplyAction myApplyAction;
  public static final String DIMENSION_KEY = "OptionsEditor";

  public OptionsEditorDialog(Project project, ConfigurableGroup[] groups, Configurable preselectedConfigurable) {
    super(project, true);
    myProject = project;
    myGroups = groups;
    myPreselected = preselectedConfigurable;

    setTitle("Settings");

    init();
  }

  protected JComponent createCenterPanel() {
    myEditor = new OptionsEditor(myProject, myGroups, myPreselected);
    myEditor.getContext().addColleague(new OptionsEditorColleague.Adapter() {
      @Override
      public void onModifiedAdded(final Configurable configurable) {
        updateStatus();
      }

      @Override
      public void onModifiedRemoved(final Configurable configurable) {
        updateStatus();
      }

      @Override
      public void onErrorsChanged() {
        updateStatus();
      }
    });
    Disposer.register(myDisposable, myEditor);
    return myEditor;
  }

  public boolean updateStatus() {
    myApplyAction.setEnabled(myEditor.canApply());

    final Map<Configurable,ConfigurationException> errors = myEditor.getContext().getErrors();
    if (errors.size() == 0) {
      setErrorText(null);
    } else {
      setErrorText("Applying changes was incomplete due to errors");
    }

    return errors.size() == 0;
  }

  @Override
  protected String getDimensionServiceKey() {
    return DIMENSION_KEY;
  }

  @Override
  protected void doOKAction() {
    if (myEditor.canApply()) {
      myEditor.apply();
      if (!updateStatus()) return;
    }

    super.doOKAction();
  }

  @Override
  public void doCancelAction(final AWTEvent source) {
    if (source instanceof KeyEvent) {
      if (myEditor.getContext().isHoldingFilter()) {
        myEditor.clearFilter();
        return;
      }
    }

    super.doCancelAction(source);
  }

  @Override
  protected Action[] createActions() {
    myApplyAction = new ApplyAction();
    return new Action[] {getOKAction(), getCancelAction(), myApplyAction, getHelpAction()};
  }

  @Override
  protected void doHelpAction() {
    final String topic = myEditor.getHelpTopic();
    if (topic != null) {
      HelpManager.getInstance().invokeHelp(topic);
    }
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myEditor.getPreferredFocusedComponent();
  }

  private class ApplyAction extends AbstractAction {
    public ApplyAction() {
      super(CommonBundle.getApplyButtonText());
      setEnabled(false);
    }

    public void actionPerformed(final ActionEvent e) {
      myEditor.apply();
    }
  }

}