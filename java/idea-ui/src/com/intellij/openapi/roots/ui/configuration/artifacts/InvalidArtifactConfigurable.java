/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.ex.MultiLineLabel;
import com.intellij.packaging.impl.artifacts.InvalidArtifact;

import javax.swing.*;

/**
 * @author nik
 */
public class InvalidArtifactConfigurable extends ArtifactConfigurableBase {
  private final String myErrorMessage;

  public InvalidArtifactConfigurable(InvalidArtifact originalArtifact,
                                     ArtifactsStructureConfigurableContextImpl artifactsStructureContext,
                                     Runnable updateTree) {
    super(originalArtifact, artifactsStructureContext, updateTree, false);
    myErrorMessage = originalArtifact.getErrorMessage();
  }

  @Override
  public void setDisplayName(String name) {
  }

  @Override
  public JComponent createOptionsPanel() {
    return new InvalidArtifactComponent(myErrorMessage).myMainPanel;
  }

  @Override
  public String getHelpTopic() {
    return null;
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
  }

  private static class InvalidArtifactComponent {
    private JPanel myMainPanel;
    private MultiLineLabel myDescriptionLabel;
    private JLabel myIconLabel;

    private InvalidArtifactComponent(String errorMessage) {
      myIconLabel.setIcon(AllIcons.RunConfigurations.ConfigurationWarning);
      myDescriptionLabel.setText(errorMessage);
    }
  }
}
