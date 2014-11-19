/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.artifacts.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorEx;
import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTreeComponent;
import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTreeSelection;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;
import com.intellij.packaging.artifacts.ArtifactPointerManager;
import com.intellij.packaging.artifacts.ModifiableArtifact;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.impl.elements.ArtifactPackagingElement;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author nik
 */
public class ExtractArtifactAction extends LayoutTreeActionBase {
  public ExtractArtifactAction(ArtifactEditorEx editor) {
    super(ProjectBundle.message("action.name.extract.artifact"), editor);
  }

  @Override
  protected boolean isEnabled() {
    return myArtifactEditor.getLayoutTreeComponent().getSelection().getCommonParentElement() != null;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final LayoutTreeComponent treeComponent = myArtifactEditor.getLayoutTreeComponent();
    final LayoutTreeSelection selection = treeComponent.getSelection();
    final CompositePackagingElement<?> parent = selection.getCommonParentElement();
    if (parent == null) return;
    final PackagingElementNode<?> parentNode = selection.getNodes().get(0).getParentNode();
    if (parentNode == null) return;

    if (!treeComponent.checkCanModifyChildren(parent, parentNode, selection.getNodes())) {
      return;
    }


    final Collection<? extends PackagingElement> selectedElements = selection.getElements();
    String initialName = "artifact";
    if (selectedElements.size() == 1) {
      initialName = PathUtil.suggestFileName(ContainerUtil.getFirstItem(selectedElements, null).createPresentation(myArtifactEditor.getContext()).getPresentableName());
    }
    IExtractArtifactDialog dialog = showDialog(treeComponent, initialName);
    if (dialog == null) return;

    final Project project = myArtifactEditor.getContext().getProject();
    final ModifiableArtifactModel model = myArtifactEditor.getContext().getOrCreateModifiableArtifactModel();
    final ModifiableArtifact artifact = model.addArtifact(dialog.getArtifactName(), dialog.getArtifactType());
    treeComponent.editLayout(new Runnable() {
      @Override
      public void run() {
        for (PackagingElement<?> element : selectedElements) {
          artifact.getRootElement().addOrFindChild(ArtifactUtil.copyWithChildren(element, project));
        }
        for (PackagingElement element : selectedElements) {
          parent.removeChild(element);
        }
        parent.addOrFindChild(new ArtifactPackagingElement(project, ArtifactPointerManager.getInstance(project).createPointer(artifact, myArtifactEditor.getContext().getArtifactModel())));
      }
    });
    treeComponent.rebuildTree();
  }

  @Nullable
  protected IExtractArtifactDialog showDialog(LayoutTreeComponent treeComponent, String initialName) {
    final ExtractArtifactDialog dialog = new ExtractArtifactDialog(myArtifactEditor.getContext(), treeComponent, initialName);
    if (!dialog.showAndGet()) {
      return null;
    }
    return dialog;
  }
}
