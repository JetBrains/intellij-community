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

package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.LanguageLevelModuleExtensionImpl;
import com.intellij.openapi.roots.impl.LanguageLevelProjectExtensionImpl;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * User: anna
 * Date: 06-Jun-2006
 */
public abstract class LanguageLevelConfigurable implements UnnamedConfigurable {
  private LanguageLevelCombo myLanguageLevelCombo;
  private JPanel myPanel = new JPanel(new GridBagLayout());

  public LanguageLevelConfigurable(final Project project) {
    myLanguageLevelCombo = new LanguageLevelCombo(ProjectBundle.message("project.language.level.combo.item")) {
      @Override
      protected LanguageLevel getDefaultLevel() {
        return LanguageLevelProjectExtensionImpl.getInstanceImpl(project).getCurrentLevel();
      }
    };
    myLanguageLevelCombo.addDefaultItem();
    myLanguageLevelCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        final Object languageLevel = myLanguageLevelCombo.getSelectedItem();
        getLanguageLevelExtension().setLanguageLevel(languageLevel instanceof LanguageLevel ? (LanguageLevel)languageLevel : null);
      }
    });

    JLabel label = new JLabel(ProjectBundle.message("module.module.language.level"));
    label.setLabelFor(myLanguageLevelCombo);
    myPanel.add(label,
                new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(12, 6, 12, 0), 0, 0));
    myPanel.add(myLanguageLevelCombo,
                new GridBagConstraints(1, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(6, 6, 12, 0), 0, 0));
  }

  @NotNull
  @Override
  public JComponent createComponent() {
    return myPanel;
  }

  @Override
  public boolean isModified() {
    return getLanguageLevelExtension().isChanged();
  }

  @Override
  public void apply() throws ConfigurationException {
    getLanguageLevelExtension().commit();
  }

  @Override
  public void reset() {
    myLanguageLevelCombo.setSelectedItem(getLanguageLevelExtension().getLanguageLevel());
  }

  @Override
  public void disposeUIResources() {
    myPanel = null;
    myLanguageLevelCombo = null;
  }

  @NotNull
  public abstract LanguageLevelModuleExtensionImpl getLanguageLevelExtension();
}
