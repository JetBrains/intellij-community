package com.intellij.openapi.roots.ui.configuration.artifacts.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorEx;
import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTreeComponent;
import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTreeSelection;
import com.intellij.openapi.ui.Messages;
import com.intellij.packaging.artifacts.ArtifactPointerManager;
import com.intellij.packaging.artifacts.ModifiableArtifact;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.impl.artifacts.PlainArtifactType;
import com.intellij.packaging.impl.elements.ArtifactPackagingElement;

import java.util.Collection;

/**
 * @author nik
 */
public class ExtractArtifactAction extends DumbAwareAction {
  private ArtifactEditorEx myEditor;

  public ExtractArtifactAction(ArtifactEditorEx editor) {
    super(ProjectBundle.message("action.name.extract.artifact"));
    myEditor = editor;
  }

  @Override
  public void update(AnActionEvent e) {
    final LayoutTreeSelection selection = myEditor.getLayoutTreeComponent().getSelection();
    e.getPresentation().setEnabled(selection.getCommonParentElement() != null);
  }

  public void actionPerformed(AnActionEvent e) {
    final LayoutTreeComponent treeComponent = myEditor.getLayoutTreeComponent();
    final LayoutTreeSelection selection = treeComponent.getSelection();
    final CompositePackagingElement<?> parent = selection.getCommonParentElement();
    if (parent == null) return;

    if (!treeComponent.checkCanRemove(selection.getNodes())) {
      return;
    }

    final Collection<? extends PackagingElement> selectedElements = selection.getElements();
    final String name = Messages.showInputDialog(myEditor.getMainComponent(), ProjectBundle.message("label.text.specify.artifact.name"),
                                                 ProjectBundle.message("dialog.title.extract.artifact"), null);
    if (name != null) {
      treeComponent.ensureRootIsWritable();
      final Project project = myEditor.getContext().getProject();
      //todo[nik] select type?
      final ModifiableArtifact artifact = myEditor.getContext().getModifiableArtifactModel().addArtifact(name, PlainArtifactType.getInstance());
      for (PackagingElement<?> element : selectedElements) {
        artifact.getRootElement().addOrFindChild(ArtifactUtil.copyWithChildren(element, project));
      }
      for (PackagingElement element : selectedElements) {
        parent.removeChild(element);
      }
      parent.addOrFindChild(new ArtifactPackagingElement(project, ArtifactPointerManager.getInstance(project).create(artifact)));
      treeComponent.rebuildTree();
    }
  }
}
