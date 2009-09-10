package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.Comparing;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author nik
 */
public class ArtifactConfigurable extends NamedConfigurable<Artifact> {
  private final Artifact myOriginalArtifact;
  private final ArtifactsStructureConfigurableContext myArtifactsStructureContext;
  private final ArtifactEditorImpl myEditor;
  private boolean myIsInUpdateName;

  public ArtifactConfigurable(Artifact originalArtifact, ArtifactsStructureConfigurableContextImpl artifactsStructureContext, final Runnable updateTree) {
    super(true, updateTree);
    myOriginalArtifact = originalArtifact;
    myArtifactsStructureContext = artifactsStructureContext;
    myEditor = artifactsStructureContext.getOrCreateEditor(originalArtifact);
  }

  public void setDisplayName(String name) {
    final String oldName = getArtifact().getName();
    if (name != null && !name.equals(oldName) && !myIsInUpdateName) {
      myArtifactsStructureContext.getModifiableArtifactModel().getOrCreateModifiableArtifact(myOriginalArtifact).setName(name);
      myEditor.updateOutputPath(oldName, name);
    }
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
    return null;
  }

  @Override
  protected JComponent createTopRightComponent() {
    final ComboBox artifactTypeBox = new ComboBox();
    for (ArtifactType type : ArtifactType.getAllTypes()) {
      artifactTypeBox.addItem(type);
    }

    artifactTypeBox.setRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        final Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        final ArtifactType type = (ArtifactType)value;
        setIcon(type.getIcon());
        setText(type.getPresentableName());
        return component;
      }
    });

    artifactTypeBox.setSelectedItem(getArtifact().getArtifactType());
    artifactTypeBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final ArtifactType selected = (ArtifactType)artifactTypeBox.getSelectedItem();
        if (!Comparing.equal(selected, getArtifact().getArtifactType())) {
          final CompositePackagingElement<?> element = myEditor.getRootElement();
          final CompositePackagingElement<?> newRootElement = selected.createRootElement(getArtifact().getName());
          myArtifactsStructureContext.getModifiableArtifactModel().getOrCreateModifiableArtifact(myOriginalArtifact).setArtifactType(selected);
          if (!newRootElement.getType().equals(element.getType())) {
            ArtifactUtil.copyChildren(element, newRootElement, myArtifactsStructureContext.getProject());
            myEditor.getLayoutTreeComponent().setRootElement(newRootElement);
          }
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
