package com.intellij.openapi.roots.ui.configuration.artifacts.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorEx;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.elements.PackagingElementType;

/**
 * @author nik
 */
public class ShowAddPackagingElementPopupAction extends DumbAwareAction {
  private final ArtifactEditorEx myArtifactEditor;

  public ShowAddPackagingElementPopupAction(ArtifactEditorEx artifactEditor) {
    super("Add...");
    myArtifactEditor = artifactEditor;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final DefaultActionGroup group = new DefaultActionGroup();
    for (PackagingElementType type : PackagingElementFactory.getInstance().getAllElementTypes()) {
      group.add(new AddNewPackagingElementAction((PackagingElementType<?>)type, myArtifactEditor));
    }
    final DataContext dataContext = e.getDataContext();
    final ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup("Add", group, dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false);
    popup.showInBestPositionFor(dataContext);
  }
}
