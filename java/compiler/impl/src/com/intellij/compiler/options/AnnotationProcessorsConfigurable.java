// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.options;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.server.BuildManager;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.compiler.ProcessorConfigProfile;

import javax.swing.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 */
public class AnnotationProcessorsConfigurable implements SearchableConfigurable, Configurable.NoScroll {

  private final Project myProject;
  private AnnotationProcessorsPanel myMainPanel;

  public AnnotationProcessorsConfigurable(final Project project) {
    myProject = project;
  }

  @Override
  public String getDisplayName() {
    return JavaCompilerBundle.message("configurable.AnnotationProcessorsConfigurable.display.name");
  }

  @Override
  public String getHelpTopic() {
    return "reference.projectsettings.compiler.annotationProcessors";
  }

  @Override
  public @NotNull String getId() {
    return getHelpTopic();
  }

  @Override
  public JComponent createComponent() {
    myMainPanel = new AnnotationProcessorsPanel(myProject);
    return myMainPanel;
  }

  @Override
  public boolean isModified() {
    final CompilerConfigurationImpl config = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject);

    if (!config.getDefaultProcessorProfile().equals(myMainPanel.getDefaultProfile())) {
      return true;
    }

    final Map<String, ProcessorConfigProfile> configProfiles = new HashMap<>();
    for (ProcessorConfigProfile profile : config.getModuleProcessorProfiles()) {
      configProfiles.put(profile.getName(), profile);
    }
    final List<ProcessorConfigProfile> panelProfiles = myMainPanel.getModuleProfiles();
    if (configProfiles.size() != panelProfiles.size()) {
      return true;
    }
    for (ProcessorConfigProfile panelProfile : panelProfiles) {
      final ProcessorConfigProfile configProfile = configProfiles.get(panelProfile.getName());
      if (configProfile == null || !configProfile.equals(panelProfile)) {
        return true;
      }
    }

    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    try {
      final CompilerConfigurationImpl config = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject);
      config.setDefaultProcessorProfile(myMainPanel.getDefaultProfile());
      config.setModuleProcessorProfiles(myMainPanel.getModuleProfiles());
    }
    finally {
      if (!myProject.isDefault()) {
        BuildManager.getInstance().clearState(myProject);
      }
    }
  }

  @Override
  public void reset() {
    final CompilerConfigurationImpl config = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject);
    myMainPanel.initProfiles(config.getDefaultProcessorProfile(), config.getModuleProcessorProfiles());
  }

  @Override
  public void disposeUIResources() {
    myMainPanel = null;
  }

}
