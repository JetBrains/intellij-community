// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.importProject;

import com.intellij.framework.detection.DetectedFrameworkDescription;
import com.intellij.framework.detection.DetectionExcludesConfiguration;
import com.intellij.framework.detection.FrameworkDetectionContext;
import com.intellij.framework.detection.FrameworkDetector;
import com.intellij.framework.detection.impl.FrameworkDetectionProcessor;
import com.intellij.framework.detection.impl.FrameworkDetectionUtil;
import com.intellij.framework.detection.impl.ui.DetectedFrameworksComponent;
import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.util.projectWizard.AbstractStepWithProgress;
import com.intellij.ide.util.projectWizard.importSources.ProjectFromSourcesBuilder;
import com.intellij.ide.util.projectWizard.importSources.impl.ProjectFromSourcesBuilderImpl;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableModelsProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public abstract class FrameworkDetectionStep extends AbstractStepWithProgress<List<? extends DetectedFrameworkDescription>>
  implements ProjectFromSourcesBuilderImpl.ProjectConfigurationUpdater {
  private final Icon myIcon;
  private List<File> myLastRoots;
  private final DetectedFrameworksComponent myDetectedFrameworksComponent;
  private JPanel myMainPanel;
  private JPanel myFrameworksPanel;
  private JLabel myFrameworksDetectedLabel;
  private final FrameworkDetectionContext myContext;

  protected FrameworkDetectionStep(final Icon icon, final ProjectFromSourcesBuilder builder) {
    super(JavaUiBundle.message("message.text.stop.searching.for.frameworks", ApplicationNamesInfo.getInstance().getProductName()));
    myIcon = icon;
    myContext = new FrameworkDetectionInWizardContext() {
      @Override
      protected List<ModuleDescriptor> getModuleDescriptors() {
        return FrameworkDetectionStep.this.getModuleDescriptors();
      }

      @Override
      protected String getContentPath() {
        return builder.getBaseProjectPath();
      }
    };
    myDetectedFrameworksComponent = new DetectedFrameworksComponent(myContext);
  }

  @Override
  public void updateDataModel() {
  }

  @Override
  protected boolean shouldRunProgress() {
    return myLastRoots == null || !Comparing.haveEqualElements(myLastRoots, getRoots());
  }

  @Override
  protected String getProgressText() {
    return JavaUiBundle.message("progress.text.searching.frameworks");
  }

  @Override
  protected JComponent createResultsPanel() {
    JComponent mainPanel = myDetectedFrameworksComponent.getMainPanel();
    myFrameworksPanel.add(mainPanel, BorderLayout.CENTER);
    return myMainPanel;
  }

  @Override
  protected List<? extends DetectedFrameworkDescription> calculate() {
    myLastRoots = getRoots();

    ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();

    List<File> roots = new ArrayList<>();
    for (ModuleDescriptor moduleDescriptor : getModuleDescriptors()) {
      roots.addAll(moduleDescriptor.getContentRoots());
    }

    FrameworkDetectionProcessor processor = new FrameworkDetectionProcessor(progressIndicator, myContext);
    return processor.processRoots(roots);
  }

  public abstract List<ModuleDescriptor> getModuleDescriptors();

  private List<File> getRoots() {
    List<File> roots = new ArrayList<>();
    for (ModuleDescriptor moduleDescriptor : getModuleDescriptors()) {
      roots.addAll(moduleDescriptor.getContentRoots());
    }
    return roots;
  }

  @Override
  protected void onFinished(final List<? extends DetectedFrameworkDescription> result, final boolean canceled) {
    myDetectedFrameworksComponent.getTree().rebuildTree(result);
    if (result.isEmpty()) {
      myFrameworksDetectedLabel.setText(JavaUiBundle.message("label.text.no.frameworks.detected"));
    }
    else {
      myFrameworksDetectedLabel.setText(JavaUiBundle.message("label.text.the.following.frameworks.are.detected"));
    }
    myFrameworksPanel.setVisible(!result.isEmpty());
  }

  @Override
  public Icon getIcon() {
    return myIcon;
  }

  public static boolean isEnabled() {
    return FrameworkDetector.EP_NAME.hasAnyExtensions();
  }

  @Override
  @NonNls
  public String getHelpId() {
    return "reference.dialogs.new.project.fromCode.facets";
  }

  @Override
  public void updateProject(@NotNull Project project, @NotNull ModifiableModelsProvider modelsProvider, @NotNull ModulesProvider modulesProvider) {
    FrameworkDetectionUtil.setupFrameworks(myDetectedFrameworksComponent.getSelectedFrameworks(), modelsProvider, modulesProvider);
    myDetectedFrameworksComponent.processUncheckedNodes(DetectionExcludesConfiguration.getInstance(project));
  }
}
