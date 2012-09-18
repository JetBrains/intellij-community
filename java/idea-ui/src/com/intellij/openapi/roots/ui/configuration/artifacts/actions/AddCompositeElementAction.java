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
package com.intellij.openapi.roots.ui.configuration.artifacts.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorEx;
import com.intellij.packaging.elements.CompositePackagingElementType;
import com.intellij.packaging.elements.PackagingElementFactory;

import java.util.List;

/**
 * @author nik
 */
public class AddCompositeElementAction extends DumbAwareAction {
  private final ArtifactEditorEx myArtifactEditor;
  private final CompositePackagingElementType<?> myElementType;

  public AddCompositeElementAction(ArtifactEditorEx artifactEditor, CompositePackagingElementType elementType) {
    super(ProjectBundle.message("artifacts.create.action", elementType.getPresentableName()));
    myArtifactEditor = artifactEditor;
    myElementType = elementType;
    getTemplatePresentation().setIcon(elementType.getCreateElementIcon());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    myArtifactEditor.addNewPackagingElement(myElementType);
  }

  public static void addCompositeCreateActions(List<AnAction> actions, final ArtifactEditorEx artifactEditor) {
    for (CompositePackagingElementType packagingElementType : PackagingElementFactory.getInstance().getCompositeElementTypes()) {
      actions.add(new AddCompositeElementAction(artifactEditor, packagingElementType));
    }
  }
}