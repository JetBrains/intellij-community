/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class EntryPointsConfigurable implements SearchableConfigurable {
  private final List<String> myAnnotations;
  private final List<EntryPointsManagerBase.ClassPattern> myModifiedPatterns;
  private final EntryPointsManagerBase myEntryPointsManager;

  public EntryPointsConfigurable(Project project) {
    final EntryPointsManagerBase entryPointsManager = (EntryPointsManagerBase)EntryPointsManager.getInstance(project);
    myAnnotations = new ArrayList<>(entryPointsManager.ADDITIONAL_ANNOTATIONS);
    myModifiedPatterns = new ArrayList<>(entryPointsManager.getPatterns());
    myEntryPointsManager = entryPointsManager;
  }

  @NotNull
  @Override
  public String getId() {
    return "EntryPoints";
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    final JPanel wholePanel = new JPanel(new GridBagLayout());
    final GridBagConstraints gc =
      new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 0.5, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, JBUI.emptyInsets(), 0, 0);
    wholePanel.add(SpecialAnnotationsUtil.createSpecialAnnotationsListControl(myAnnotations, "Mark as entry point if annotated by", true), gc);
    wholePanel.add(new ClassPatternsPanel(myModifiedPatterns), gc);
    return wholePanel;
  }

  @Override
  public void apply() throws ConfigurationException {
    myEntryPointsManager.ADDITIONAL_ANNOTATIONS.clear();
    myEntryPointsManager.ADDITIONAL_ANNOTATIONS.addAll(myAnnotations);

    myEntryPointsManager.getPatterns().clear();
    myEntryPointsManager.getPatterns().addAll(myModifiedPatterns);
  }

  @Override
  public void reset() {
    myAnnotations.clear();
    myAnnotations.addAll(myEntryPointsManager.ADDITIONAL_ANNOTATIONS);

    myModifiedPatterns.clear();
    myModifiedPatterns.addAll(myEntryPointsManager.getPatterns());
  }

  @Override
  public boolean isModified() {
    return !myAnnotations.equals(myEntryPointsManager.ADDITIONAL_ANNOTATIONS) ||
           !myModifiedPatterns.equals(myEntryPointsManager.getPatterns());
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Project Entry Points";
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return getId();
  }
}
