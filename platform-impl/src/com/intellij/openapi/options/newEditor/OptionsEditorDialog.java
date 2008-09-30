package com.intellij.openapi.options.newEditor;

import com.intellij.CommonBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.Map;

public class OptionsEditorDialog extends DialogWrapper {

  private Project myProject;
  private ConfigurableGroup[] myGroups;
  private Configurable myPreselected;
  private OptionsEditor myEditor;

  private ApplyAction myApplyAction;

  public OptionsEditorDialog(Project project, ConfigurableGroup[] groups, Configurable preselectedConfigurable) {
    super(project, true);
    myProject = project;
    myGroups = groups;
    myPreselected = preselectedConfigurable;

    init();
  }

  protected JComponent createCenterPanel() {
    myEditor = new OptionsEditor(myProject, myGroups, myPreselected);
    myEditor.getContext().addColleague(new OptionsEditorColleague.Adapter() {
      @Override
      public void onModifiedAdded(final Configurable configurable) {
        myApplyAction.setEnabled(myEditor.canApply());
      }

      @Override
      public void onModifiedRemoved(final Configurable configurable) {
        myApplyAction.setEnabled(myEditor.canApply());
      }

      @Override
      public void onErrorsChanged() {
        final Map<Configurable,ConfigurationException> errors = myEditor.getContext().getErrors();
        if (errors.size() == 0) {
          setErrorText(null);
        } else {
          setErrorText("Applying changes was incomplete due to errors");
        }
      }
    });
    Disposer.register(myDisposable, myEditor);
    return myEditor;
  }

  @Override
  protected String getDimensionServiceKey() {
    return "OptionsEditor";
  }

  @Override
  protected Action[] createActions() {
    myApplyAction = new ApplyAction();
    return new Action[] {getOKAction(), getCancelAction(), myApplyAction};
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