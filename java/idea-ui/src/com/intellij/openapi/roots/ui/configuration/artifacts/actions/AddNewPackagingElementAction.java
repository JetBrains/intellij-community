package com.intellij.openapi.roots.ui.configuration.artifacts.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorEx;
import com.intellij.packaging.elements.PackagingElementType;

/**
* @author nik
*/
public class AddNewPackagingElementAction extends DumbAwareAction {
  private final PackagingElementType<?> myType;
  private final ArtifactEditorEx myArtifactEditor;

  public AddNewPackagingElementAction(PackagingElementType<?> type, ArtifactEditorEx artifactEditor) {
    super(type.getPresentableName(), null, type.getCreateElementIcon());
    myType = type;
    myArtifactEditor = artifactEditor;
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setVisible(myType.canCreate(myArtifactEditor.getContext(), myArtifactEditor.getArtifact()));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    myArtifactEditor.addNewPackagingElement(myType);
  }
}
