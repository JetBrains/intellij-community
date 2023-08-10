// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.importProject;

import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.util.newProjectWizard.StepSequence;
import com.intellij.ide.util.projectWizard.AbstractStepWithProgress;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ide.util.projectWizard.importSources.ProjectStructureDetector;
import com.intellij.ide.util.projectWizard.importSources.impl.ProjectFromSourcesBuilderImpl;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.ui.ex.MultiLineLabel;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class RootsDetectionStep extends AbstractStepWithProgress<List<DetectedRootData>> {
  private static final String ROOTS_FOUND_CARD = "roots_found";
  private static final String ROOTS_NOT_FOUND_CARD = "roots_not_found";
  private final ProjectFromSourcesBuilderImpl myBuilder;
  private final WizardContext myContext;
  private final StepSequence mySequence;
  private final Icon myIcon;
  private final String myHelpId;
  private DetectedRootsChooser myDetectedRootsChooser;
  private String myCurrentBaseProjectPath;
  private JPanel myResultPanel;

  public RootsDetectionStep(ProjectFromSourcesBuilderImpl builder,
                            WizardContext context,
                            StepSequence sequence,
                            Icon icon,
                            @NonNls String helpId) {
    super(JavaUiBundle.message("prompt.stop.searching.for.sources", ApplicationNamesInfo.getInstance().getProductName()));
    myBuilder = builder;
    myContext = context;
    mySequence = sequence;
    myIcon = icon;
    myHelpId = helpId;
  }

  @Override
  protected JComponent createResultsPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    myDetectedRootsChooser = new DetectedRootsChooser();
    myDetectedRootsChooser.addSelectionListener(() -> updateSelectedTypes());
    final String text = JavaUiBundle.message("label.project.roots.have.been.found");
    final JLabel label = new JLabel(text);
    label.setUI(new MultiLineLabelUI());
    panel.add(label, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST,
                                            GridBagConstraints.HORIZONTAL, JBUI.insets(8, 10, 0, 10), 0, 0));
    panel.add(myDetectedRootsChooser.getComponent(),
              new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
                                     JBInsets.create(8, 10), 0, 0));

    final JButton markAllButton = new JButton(JavaUiBundle.message("button.mark.all"));
    panel.add(markAllButton,
              new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                     JBUI.insets(0, 10, 8, 2), 0, 0));

    final JButton unmarkAllButton = new JButton(JavaUiBundle.message("button.unmark.all"));
    panel.add(unmarkAllButton,
              new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                     JBUI.insets(0, 0, 8, 10), 0, 0));

    markAllButton.addActionListener(e -> myDetectedRootsChooser.setAllElementsMarked(true));
    unmarkAllButton.addActionListener(e -> myDetectedRootsChooser.setAllElementsMarked(false));

    myResultPanel = new JPanel(new CardLayout());
    myResultPanel.add(ROOTS_FOUND_CARD, panel);
    JPanel notFoundPanel = new JPanel(new BorderLayout());
    notFoundPanel.setBorder(JBUI.Borders.empty(5));
    notFoundPanel.add(BorderLayout.NORTH, new MultiLineLabel(JavaUiBundle.message("label.project.roots.not.found")));
    myResultPanel.add(ROOTS_NOT_FOUND_CARD, notFoundPanel);
    return myResultPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myDetectedRootsChooser.getComponent();
  }

  @Override
  public void updateDataModel() {
    final List<DetectedRootData> selectedElements = myDetectedRootsChooser.getMarkedElements();
    myBuilder.setupProjectStructure(RootDetectionProcessor.createRootsMap(selectedElements));
    updateSelectedTypes();
  }

  private void updateSelectedTypes() {
    Set<String> selectedTypes = new LinkedHashSet<>();

    selectedTypes.add(JavaUiBundle.message("existing.sources"));

    for (DetectedRootData rootData : myDetectedRootsChooser.getMarkedElements()) {
      for (ProjectStructureDetector detector : rootData.getSelectedDetectors()) {
        selectedTypes.add(detector.getDetectorId());
      }
    }

    mySequence.setTypes(selectedTypes);
    myContext.requestWizardButtonsUpdate();
  }

  @Override
  protected boolean shouldRunProgress() {
    final String baseProjectPath = getBaseProjectPath();
    return myCurrentBaseProjectPath == null ? baseProjectPath != null : !myCurrentBaseProjectPath.equals(baseProjectPath);
  }

  @Override
  protected void onFinished(final List<DetectedRootData> foundRoots, final boolean canceled) {
    final CardLayout layout = (CardLayout)myResultPanel.getLayout();
    if (foundRoots != null && !foundRoots.isEmpty() && !canceled) {
      myCurrentBaseProjectPath = getBaseProjectPath();
      myDetectedRootsChooser.setElements(foundRoots);
      updateSelectedTypes();
      layout.show(myResultPanel, ROOTS_FOUND_CARD);
    }
    else {
      myCurrentBaseProjectPath = null;
      layout.show(myResultPanel, ROOTS_NOT_FOUND_CARD);
    }
    myResultPanel.revalidate();
  }

  @Override
  protected List<DetectedRootData> calculate() {
    final String baseProjectPath = getBaseProjectPath();
    if (baseProjectPath == null) {
      return Collections.emptyList();
    }

    return RootDetectionProcessor.detectRoots(new File(baseProjectPath));
  }


  @Nullable
  private String getBaseProjectPath() {
    return myBuilder.getBaseProjectPath();
  }

  @Override
  protected String getProgressText() {
    final String root = getBaseProjectPath();
    return JavaUiBundle.message("progress.searching.for.sources", root != null ? root.replace('/', File.separatorChar) : "");
  }

  @Override
  public Icon getIcon() {
    return myIcon;
  }

  @Override
  public String getHelpId() {
    return myHelpId;
  }
}
