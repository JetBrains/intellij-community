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
package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Comparing;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.ui.SimpleListCellRenderer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ArtifactConfigurable extends ArtifactConfigurableBase {
  public ArtifactConfigurable(Artifact originalArtifact, ArtifactsStructureConfigurableContextImpl artifactsStructureContext, final Runnable updateTree) {
    super(originalArtifact, artifactsStructureContext, updateTree, true);
  }

  @Override
  public void setDisplayName(String name) {
    final String oldName = getArtifact().getName();
    if (name != null && !name.equals(oldName) && !isUpdatingNameFieldFromDisplayName()) {
      ModifiableArtifactModel modifiableArtifactModel = myArtifactsStructureContext.getOrCreateModifiableArtifactModel();
      // Modify artifact name only in case no other artifacts have the same name because names should be unique between the artifacts
      if (modifiableArtifactModel.findArtifact(name) == null) {
        modifiableArtifactModel.getOrCreateModifiableArtifact(myOriginalArtifact).setName(name);
        getEditor().updateOutputPath(oldName, name);
      }
    }
  }

  @Override
  public JComponent createOptionsPanel() {
    return getEditor().createMainComponent();
  }

  @Override
  protected JComponent createTopRightComponent() {
    final ComboBox<ArtifactType> artifactTypeBox = new ComboBox<>();
    for (ArtifactType type : ArtifactType.getAllTypes()) {
      artifactTypeBox.addItem(type);
    }

    artifactTypeBox.setRenderer(SimpleListCellRenderer.create((label, value, index) -> {
      label.setIcon(value.getIcon());
      label.setText(value.getPresentableName());
    }));

    artifactTypeBox.setSelectedItem(getArtifact().getArtifactType());
    artifactTypeBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final ArtifactType selected = (ArtifactType)artifactTypeBox.getSelectedItem();
        if (selected != null && !Comparing.equal(selected, getArtifact().getArtifactType())) {
          getEditor().setArtifactType(selected);
        }
      }
    });

    final JPanel panel = new JPanel(new FlowLayout());
    JLabel artifactTypeBoxLabel = new JLabel(JavaUiBundle.message("label.artifact.configurable.type"));
    artifactTypeBoxLabel.setLabelFor(artifactTypeBox);
    panel.add(artifactTypeBoxLabel);
    panel.add(artifactTypeBox);
    return panel;
  }

  @Override
  public boolean isModified() {
    return getEditor().isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    getEditor().apply();
  }

  @Override
  public String getHelpTopic() {
    return getEditor().getHelpTopic();
  }

  private ArtifactEditorImpl getEditor() {
    return myArtifactsStructureContext.getOrCreateEditor(myOriginalArtifact);
  }
}
