/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.file.exclude.ui;

import com.intellij.openapi.file.exclude.ProjectFileExclusionManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Rustam Vishnyakov
 */
public class ExcludedFilesConfigurable implements SearchableConfigurable {

  private final ExcludedFilesPanel myExcludedFilesPanel;
  private final ProjectFileExclusionManager myExclusionManager;

  public ExcludedFilesConfigurable(Project project) {
    myExclusionManager = ProjectFileExclusionManager.getInstance(project);
    myExcludedFilesPanel = new ExcludedFilesPanel(myExclusionManager != null ? myExclusionManager.getSortedFiles() : null);
  }

  @NotNull
  @Override
  public String getId() {
    return getDisplayName();
  }

  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Excluded Files"; //TODO<rv> Move to resources
  }

  @Override
  public Icon getIcon() {
    return null;
  }

  @Override
  public String getHelpTopic() {
    return null;
  }

  @Override
  public JComponent createComponent() {
    return myExcludedFilesPanel.getTopJPanel();
  }

  @Override
  public boolean isModified() {
    if (myExclusionManager == null) return false;
    Collection<VirtualFile> currFiles = myExclusionManager.getExcludedFiles();
    Collection<VirtualFile> newFiles = myExcludedFilesPanel.getExcludedFiles();
    for (VirtualFile file : currFiles) {
      if (!newFiles.contains(file)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myExclusionManager == null) return;
    final Collection<VirtualFile> currFiles = myExclusionManager.getExcludedFiles();
    final Collection<VirtualFile> newFiles = myExcludedFilesPanel.getExcludedFiles();
    final List<VirtualFile> putBackFiles = new ArrayList<VirtualFile>();
    for (VirtualFile file : currFiles) {
      if (!newFiles.contains(file)) {
        putBackFiles.add(file);
      }
    }
    for (VirtualFile file : putBackFiles) {
      myExclusionManager.removeExclusion(file);
    }
  }

  @Override
  public void reset() {
    myExcludedFilesPanel.resetFiles(myExclusionManager != null ? myExclusionManager.getSortedFiles() : null);
  }

  @Override
  public void disposeUIResources() {
    //
  }
}
