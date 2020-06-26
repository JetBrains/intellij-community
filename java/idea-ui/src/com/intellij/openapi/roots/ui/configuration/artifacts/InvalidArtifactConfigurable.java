// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.ex.MultiLineLabel;
import com.intellij.packaging.impl.artifacts.InvalidArtifact;

import javax.swing.*;

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

  private static final class InvalidArtifactComponent {
    private JPanel myMainPanel;
    private MultiLineLabel myDescriptionLabel;
    private JLabel myIconLabel;

    private InvalidArtifactComponent(String errorMessage) {
      myIconLabel.setIcon(AllIcons.General.BalloonError);
      myDescriptionLabel.setText(errorMessage);
    }
  }
}
