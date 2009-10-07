package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.packaging.elements.ComplexPackagingElementType;

/**
 * @author nik
 */
public class ToggleShowElementContentAction extends ToggleAction implements DumbAware {
  private final ComplexPackagingElementType<?> myType;
  private final ArtifactEditorImpl myEditor;

  public ToggleShowElementContentAction(ComplexPackagingElementType<?> type, ArtifactEditorImpl editor) {
    super(type.getShowContentActionText());
    myType = type;
    myEditor = editor;
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return myEditor.getSubstitutionParameters().isShowContentForType(myType);
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    myEditor.getSubstitutionParameters().setShowContent(myType, state);
    myEditor.updateShowContentCheckbox();
    myEditor.getLayoutTreeComponent().rebuildTree();
  }
}
