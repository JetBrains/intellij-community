/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.util.importProject;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.projectWizard.AbstractStepWithProgress;
import com.intellij.ide.util.projectWizard.importSources.DetectedProjectRoot;
import com.intellij.ide.util.projectWizard.importSources.ProjectFromSourcesBuilder;
import com.intellij.ide.util.projectWizard.importSources.ProjectStructureDetector;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 18, 2007
 */
public class ModulesDetectionStep extends AbstractStepWithProgress<List<ModuleDescriptor>> {
  private final ProjectStructureDetector myDetector;
  private final ProjectFromSourcesBuilder myBuilder;
  private final ProjectDescriptor myProjectDescriptor;
  private final ModuleInsight myInsight;
  private final Icon myIcon;
  private final String myHelpId;
  private ModulesLayoutPanel myModulesLayoutPanel;

  public ModulesDetectionStep(ProjectStructureDetector detector,
                              ProjectFromSourcesBuilder builder,
                              ProjectDescriptor projectDescriptor, final ModuleInsight insight,
                              Icon icon,
                              @NonNls String helpId) {
    super("Stop module analysis?");
    myDetector = detector;
    myBuilder = builder;
    myProjectDescriptor = projectDescriptor;
    myInsight = insight;
    myIcon = icon;
    myHelpId = helpId;
  }

  public void updateDataModel() {
    myProjectDescriptor.setModules(myModulesLayoutPanel.getChosenEntries());
  }

  protected JComponent createResultsPanel() {
    myModulesLayoutPanel = new ModulesLayoutPanel(myInsight, new ModulesLayoutPanel.LibraryFilter() {
      public boolean isLibraryChosen(final LibraryDescriptor libDescriptor) {
        return myProjectDescriptor.isLibraryChosen(libDescriptor);
      }
    });
    return myModulesLayoutPanel;
  }

  protected String getProgressText() {
    return "Searching for modules. Please wait.";
  }

  int myPreviousStateHashCode = -1;
  protected boolean shouldRunProgress() {
    final int currentHash = calcStateHashCode();
    try {
      return currentHash != myPreviousStateHashCode;
    }
    finally {
      myPreviousStateHashCode = currentHash;
    }
  }

  private int calcStateHashCode() {
    final String contentEntryPath = myBuilder.getBaseProjectPath();
    int hash = contentEntryPath != null? contentEntryPath.hashCode() : 1;
    for (DetectedProjectRoot root : myBuilder.getProjectRoots(myDetector)) {
      hash = 31 * hash + root.getDirectory().hashCode();
    }
    final List<LibraryDescriptor> libs = myProjectDescriptor.getLibraries();
    for (LibraryDescriptor lib : libs) {
      final Collection<File> files = lib.getJars();
      for (File file : files) {
        hash = 31 * hash + file.hashCode();
      }
    }
    return hash;
  }

  protected List<ModuleDescriptor> calculate() {
    myInsight.scanModules();
    final List<ModuleDescriptor> suggestedModules = myInsight.getSuggestedModules();
    return suggestedModules != null? suggestedModules : Collections.<ModuleDescriptor>emptyList();
  }

  @Override
  public boolean validate() throws ConfigurationException {
    final boolean validated = super.validate();
    if (!validated) {
      return false;
    }

    final List<ModuleDescriptor> modules = myModulesLayoutPanel.getChosenEntries();
    final Map<String, ModuleDescriptor> errors = new LinkedHashMap<>();
    for (ModuleDescriptor module : modules) {
      try {
        final String moduleFilePath = module.computeModuleFilePath();
        if (new File(moduleFilePath).exists()) {
          errors.put(IdeBundle.message("warning.message.the.module.file.0.already.exist.and.will.be.overwritten", moduleFilePath), module);
        }
      }
      catch (InvalidDataException e) {
        errors.put(e.getMessage(), module);
      }
    }
    if (!errors.isEmpty()) {
      final int answer = Messages.showYesNoCancelDialog(getComponent(),
                                                        IdeBundle.message("warning.text.0.do.you.want.to.overwrite.these.files",
                                                                          StringUtil.join(errors.keySet(), "\n"), errors.size()),
                                                        IdeBundle.message("title.file.already.exists"), "Overwrite", "Reuse", "Cancel", Messages.getQuestionIcon());
      if (answer == Messages.CANCEL) {
        return false;
      }

      if (answer != Messages.YES) {
        for (ModuleDescriptor moduleDescriptor : errors.values()) {
          moduleDescriptor.reuseExisting(true);
        }
      }
    }
    return true;
  }

  protected void onFinished(final List<ModuleDescriptor> moduleDescriptors, final boolean canceled) {
    myModulesLayoutPanel.rebuild();
  }

  @NonNls
  public String getHelpId() {
    return myHelpId;
  }

  public Icon getIcon() {
    return myIcon;
  }
}
