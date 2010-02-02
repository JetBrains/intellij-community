/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorEx;
import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTreeComponent;
import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTreeSelection;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.CompositePackagingElementType;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * @author nik
 */
public class SurroundElementWithAction extends LayoutTreeActionBase {
  public SurroundElementWithAction(ArtifactEditorEx artifactEditor) {
    super("Surround With...", artifactEditor);
    final CustomShortcutSet shortcutSet = new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts("SurroundWith"));
    registerCustomShortcutSet(shortcutSet, artifactEditor.getLayoutTreeComponent().getLayoutTree());
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

    final CompositePackagingElementType<?>[] types = PackagingElementFactory.getInstance().getCompositeElementTypes();
    final List<PackagingElement<?>> selected = selection.getElements();
    if (types.length == 1) {
      surroundWith(types[0], parent, selected, treeComponent);
    }
    else {
      JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<CompositePackagingElementType>("Surround With...", types) {
        @Override
        public Icon getIconFor(CompositePackagingElementType aValue) {
          return aValue.getCreateElementIcon();
        }

        @NotNull
        @Override
        public String getTextFor(CompositePackagingElementType value) {
          return value.getPresentableName();
        }

        @Override
        public PopupStep onChosen(final CompositePackagingElementType selectedValue, boolean finalChoice) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              surroundWith(selectedValue, parent, selected, treeComponent);
            }
          });
          return FINAL_CHOICE;
        }
      }).showInBestPositionFor(e.getDataContext());
    }
  }

  private void surroundWith(final CompositePackagingElementType<?> type, final CompositePackagingElement<?> parent, final List<PackagingElement<?>> selected,
                            LayoutTreeComponent treeComponent) {
    if (myArtifactEditor.isDisposed() || selected.isEmpty()) return;

    final Project project = myArtifactEditor.getContext().getProject();
    final String elementName = ContainerUtil.getFirstItem(selected, null).createPresentation(myArtifactEditor.getContext()).getPresentableName();
    final String baseName = PathUtil.suggestFileName(elementName);
    final CompositePackagingElement<?> newParent = type.createComposite(parent, baseName, myArtifactEditor.getContext());
    if (newParent != null) {
      treeComponent.editLayout(new Runnable() {
        public void run() {
          for (PackagingElement<?> element : selected) {
            newParent.addOrFindChild(ArtifactUtil.copyWithChildren(element, project));
          }
          for (PackagingElement<?> element : selected) {
            parent.removeChild(element);
          }
          parent.addOrFindChild(newParent);
        }
      });
      treeComponent.rebuildTree();
    }
  }
}
