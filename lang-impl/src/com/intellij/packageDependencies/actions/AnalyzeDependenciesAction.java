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
  private JCheckBox myTransitiveCB;
  private JPanel myWholePanel;
  private JSpinner myBorderChooser;

  public AnalyzeDependenciesAction() {
    super(AnalysisScopeBundle.message("action.forward.dependency.analysis"), AnalysisScopeBundle.message("action.analysis.noun"));
    myBorderChooser.setModel(new SpinnerNumberModel(5, 0, Integer.MAX_VALUE, 1));
    myTransitiveCB.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        myBorderChooser.setEnabled(myTransitiveCB.isSelected());
      }
    });
    myBorderChooser.setEnabled(myTransitiveCB.isSelected());
  }

  protected void analyze(@NotNull final Project project, AnalysisScope scope) {
    new AnalyzeDependenciesHandler(project, scope, myTransitiveCB.isSelected() ? ((SpinnerNumberModel)myBorderChooser.getModel()).getNumber().intValue() : 0).analyze();
  }

  @Nullable
  protected JComponent getAdditionalActionSettings(final Project project, final BaseAnalysisActionDialog dialog) {
    myTransitiveCB.setText("Show transitive dependencies. Do not travel deeper than");
    return myWholePanel;
  }
}
