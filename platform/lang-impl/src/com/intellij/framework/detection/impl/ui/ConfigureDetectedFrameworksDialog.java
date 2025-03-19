// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.framework.detection.impl.ui;

import com.intellij.framework.detection.DetectedFrameworkDescription;
import com.intellij.framework.detection.DetectionExcludesConfiguration;
import com.intellij.framework.detection.impl.FrameworkDetectionContextImpl;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.*;
import java.util.List;

@ApiStatus.Internal
public final class ConfigureDetectedFrameworksDialog extends DialogWrapper {
  private final DetectedFrameworksComponent myComponent;
  private final Project myProject;

  public ConfigureDetectedFrameworksDialog(Project project, List<? extends DetectedFrameworkDescription> descriptions) {
    super(project, true);
    myProject = project;
    setTitle(LangBundle.message("dialog.title.setup.frameworks"));
    myComponent = new DetectedFrameworksComponent(new FrameworkDetectionContextImpl(project));
    myComponent.getTree().rebuildTree(descriptions);
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myComponent.getMainPanel();
  }

  public List<DetectedFrameworkDescription> getSelectedFrameworks() {
    return myComponent.getSelectedFrameworks();
  }

  @Override
  protected void doOKAction() {
    myComponent.processUncheckedNodes(DetectionExcludesConfiguration.getInstance(myProject));
    super.doOKAction();
  }
}
