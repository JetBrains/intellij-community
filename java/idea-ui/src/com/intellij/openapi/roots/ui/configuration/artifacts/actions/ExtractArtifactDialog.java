// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.artifacts.actions;

import com.intellij.CommonBundle;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactTypeCellRenderer;
import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTreeComponent;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.impl.artifacts.PlainArtifactType;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;

/**
 * @author nik
 */
public class ExtractArtifactDialog extends DialogWrapper implements IExtractArtifactDialog {
  private JPanel myMainPanel;
  private JTextField myNameField;
  private JComboBox myTypeBox;
  private final ArtifactEditorContext myContext;

  public ExtractArtifactDialog(ArtifactEditorContext context, LayoutTreeComponent treeComponent, String initialName) {
    super(treeComponent.getLayoutTree(), true);
    myContext = context;
    setTitle(ProjectBundle.message("dialog.title.extract.artifact"));
    for (ArtifactType type : ArtifactType.getAllTypes()) {
      myTypeBox.addItem(type);
    }
    myTypeBox.setSelectedItem(PlainArtifactType.getInstance());
    myTypeBox.setRenderer(new ArtifactTypeCellRenderer(myTypeBox.getRenderer()));
    myNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        setOKActionEnabled(!StringUtil.isEmptyOrSpaces(getArtifactName()));
      }
    });
    myNameField.setText(initialName);
    init();
  }

  @Override
  protected void doOKAction() {
    final String artifactName = getArtifactName();
    if (myContext.getArtifactModel().findArtifact(artifactName) != null) {
      Messages.showErrorDialog(myContext.getProject(), "Artifact '" + artifactName + "' already exists!", CommonBundle.getErrorTitle());
      return;
    }
    super.doOKAction();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  @Override
  public String getArtifactName() {
    return myNameField.getText();
  }

  @Override
  public ArtifactType getArtifactType() {
    return (ArtifactType)myTypeBox.getSelectedItem();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }
}
