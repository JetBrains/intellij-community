// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.cache.ui;

import com.intellij.compiler.cache.CompilerCacheLoadingSettings;
import com.intellij.compiler.options.ComparingUtils;
import com.intellij.compiler.server.BuildManager;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class CompilerCacheConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private JPanel myPanel;
  private final Project myProject;
  private JBLabel  myForceUpdateDescription;
  private JBCheckBox myCbForceUpdateCaches;
  private JBCheckBox myCbDisableCacheDownload;
  private JTextField myMaxDownloadDurationField;
  private JBLabel myMaxDownloadDurationDescription;

  public CompilerCacheConfigurable(final Project project) {
    myProject = project;
    myForceUpdateDescription.setText("<html>Turn off the heuristic that determines when it's faster to download cache or build locally and force downloads the nearest cache at each build</html>"); //NON-NLS
    myMaxDownloadDurationDescription.setText("<html>Set maximum applicable time of caches download. If the approximate time of work will be higher local build will be executed.</html>"); //NON-NLS
  }

  @Override
  public String getDisplayName() {
    return JavaCompilerBundle.message("compiler.cache.description");
  }

  @Override
  @NotNull
  public String getId() {
    return "reference.projectsettings.compiler.compilercache";
  }

  @Override
  public JComponent createComponent() {
    return myPanel;
  }

  @Override
  public boolean isModified() {
    boolean isModified = ComparingUtils.isModified(myCbForceUpdateCaches, CompilerCacheLoadingSettings.getForceUpdateValue());
    isModified |= ComparingUtils.isModified(myCbDisableCacheDownload, CompilerCacheLoadingSettings.getDisableUpdateValue());
    isModified |= ComparingUtils.isModified(myMaxDownloadDurationField, String.valueOf(CompilerCacheLoadingSettings.getMaxDownloadDuration()));
    return isModified;
  }

  @Override
  public void apply() throws ConfigurationException {
    try {
      CompilerCacheLoadingSettings.saveForceUpdateValue(myCbForceUpdateCaches.isSelected());
      CompilerCacheLoadingSettings.saveDisableUpdateValue(myCbDisableCacheDownload.isSelected());
      CompilerCacheLoadingSettings.saveMaxDownloadDuration(Integer.parseInt(myMaxDownloadDurationField.getText()));
    }
    finally {
      if (!myProject.isDefault()) {
        BuildManager.getInstance().clearState(myProject);
      }
    }
  }

  @Override
  public void reset() {
    myCbForceUpdateCaches.setSelected(CompilerCacheLoadingSettings.getForceUpdateValue());
    myCbDisableCacheDownload.setSelected(CompilerCacheLoadingSettings.getDisableUpdateValue());
    myMaxDownloadDurationField.setText(String.valueOf(CompilerCacheLoadingSettings.getMaxDownloadDuration()));
  }
}
