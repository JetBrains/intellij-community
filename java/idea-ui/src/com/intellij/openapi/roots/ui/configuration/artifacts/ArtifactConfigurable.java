/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectStructureElementConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Comparing;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactType;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author nik
 */
public class ArtifactConfigurable extends ProjectStructureElementConfigurable<Artifact> {
  private final Artifact myOriginalArtifact;
  private final ArtifactsStructureConfigurableContext myArtifactsStructureContext;
  private final ArtifactEditorImpl myEditor;
  private boolean myIsInUpdateName;
  private final ProjectStructureElement myProjectStructureElement;

  public ArtifactConfigurable(Artifact originalArtifact, ArtifactsStructureConfigurableContextImpl artifactsStructureContext, final Runnable updateTree) {
    super(true, updateTree);
    myOriginalArtifact = originalArtifact;
    myArtifactsStructureContext = artifactsStructureContext;
    myEditor = artifactsStructureContext.getOrCreateEditor(originalArtifact);
    myProjectStructureElement = myArtifactsStructureContext.getOrCreateArtifactElement(myOriginalArtifact);
  }

  public void setDisplayName(String name) {
    final String oldName = getArtifact().getName();
    if (name != null && !name.equals(oldName) && !myIsInUpdateName) {
      myArtifactsStructureContext.getOrCreateModifiableArtifactModel().getOrCreateModifiableArtifact(myOriginalArtifact).setName(name);
      myEditor.updateOutputPath(oldName, name);
    }
  }

  @Override
  public ProjectStructureElement getProjectStructureElement() {
    return myProjectStructureElement;
  }

  @Override
  public void updateName() {
    myIsInUpdateName = true;
    try {
      super.updateName();
    }
    finally {
      myIsInUpdateName = false;
    }
  }

  private Artifact getArtifact() {
    return myArtifactsStructureContext.getArtifactModel().getArtifactByOriginal(myOriginalArtifact);
  }

  public Artifact getEditableObject() {
    return getArtifact();
  }

  public String getBannerSlogan() {
    return ProjectBundle.message("banner.slogan.artifact.0", getDisplayName());
  }

  public JComponent createOptionsPanel() {
    return myEditor.createMainComponent();
  }

  @Nls
  public String getDisplayName() {
    return getArtifact().getName();
  }

  public Icon getIcon() {
    return getArtifact().getArtifactType().getIcon();
  }

  public String getHelpTopic() {
    return "reference.settingsdialog.project.structure.artifacts";
  }

  @Override
  protected JComponent createTopRightComponent() {
    final ComboBox artifactTypeBox = new ComboBox();
    for (ArtifactType type : ArtifactType.getAllTypes()) {
      artifactTypeBox.addItem(type);
    }

    artifactTypeBox.setRenderer(new ArtifactTypeCellRenderer());

    artifactTypeBox.setSelectedItem(getArtifact().getArtifactType());
    artifactTypeBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final ArtifactType selected = (ArtifactType)artifactTypeBox.getSelectedItem();
        if (selected != null && !Comparing.equal(selected, getArtifact().getArtifactType())) {
          myEditor.setArtifactType(selected);
        }
      }
    });

    final JPanel panel = new JPanel(new FlowLayout());
    panel.add(new JLabel("Type: "));
    panel.add(artifactTypeBox);
    return panel;
  }

  public boolean isModified() {
    return myEditor.isModified();
  }

  public void apply() throws ConfigurationException {
    myEditor.apply();
  }

  public void reset() {
  }

  public void disposeUIResources() {
  }

}
