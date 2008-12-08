package com.intellij.packageDependencies.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.analysis.BaseAnalysisAction;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class AnalyzeDependenciesAction extends BaseAnalysisAction {
  private AnalyzeDependenciesSettingPanel myPanel;
  
  public AnalyzeDependenciesAction() {
    super(AnalysisScopeBundle.message("action.forward.dependency.analysis"), AnalysisScopeBundle.message("action.analysis.noun"));

  }

  protected void analyze(@NotNull final Project project, AnalysisScope scope) {
    new AnalyzeDependenciesHandler(project, scope, myPanel.myTransitiveCB.isSelected() ? ((SpinnerNumberModel)myPanel.myBorderChooser.getModel()).getNumber().intValue() : 0).analyze();
    myPanel = null;
  }

  @Nullable
  protected JComponent getAdditionalActionSettings(final Project project, final BaseAnalysisActionDialog dialog) {
    myPanel = new AnalyzeDependenciesSettingPanel();
    myPanel.myTransitiveCB.setText("Show transitive dependencies. Do not travel deeper than");
    myPanel.myTransitiveCB.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        myPanel.myBorderChooser.setEnabled(myPanel.myTransitiveCB.isSelected());
      }
    });
    myPanel.myBorderChooser.setModel(new SpinnerNumberModel(5, 0, Integer.MAX_VALUE, 1));
    myPanel.myBorderChooser.setEnabled(myPanel.myTransitiveCB.isSelected());
    return myPanel.myWholePanel;
  }

  @Override
  protected void canceled() {
    super.canceled();
    myPanel = null;
  }

  private static class AnalyzeDependenciesSettingPanel {
    private JCheckBox myTransitiveCB;
    private JPanel myWholePanel;
    private JSpinner myBorderChooser;
  }

}
