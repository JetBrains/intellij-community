/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorEx;
import com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems.SourceItemsTree;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.elements.PackagingElementOutputKind;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.impl.artifacts.PackagingElementPath;
import com.intellij.packaging.impl.artifacts.PackagingElementProcessor;
import com.intellij.packaging.impl.elements.FileCopyPackagingElement;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingSourceItem;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class ExtractIntoDefaultLocationAction extends PutIntoDefaultLocationActionBase {
  public ExtractIntoDefaultLocationAction(SourceItemsTree sourceItemsTree, ArtifactEditorEx artifactEditor) {
    super(sourceItemsTree, artifactEditor);
  }

  @Override
  public void update(AnActionEvent e) {
    final String pathForClasses = myArtifactEditor.getArtifact().getArtifactType().getDefaultPathFor(PackagingElementOutputKind.DIRECTORIES_WITH_CLASSES);
    final Presentation presentation = e.getPresentation();
    if (onlyJarsSelected() && pathForClasses != null) {
      presentation.setText("Extract Into " + getTargetLocationText(Collections.singleton(pathForClasses)));
      presentation.setVisible(true);
    }
    else {
      presentation.setVisible(false);
    }
  }

  private boolean onlyJarsSelected() {
    for (PackagingSourceItem item : mySourceItemsTree.getSelectedItems()) {
      if (item.isProvideElements() && (!item.getKindOfProducedElements().containsJarFiles() || item.getKindOfProducedElements().containsDirectoriesWithClasses())) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final String pathForClasses = myArtifactEditor.getArtifact().getArtifactType().getDefaultPathFor(PackagingElementOutputKind.DIRECTORIES_WITH_CLASSES);
    if (pathForClasses != null) {
      final List<PackagingElement<?>> extracted = new ArrayList<>();
      for (PackagingSourceItem item : mySourceItemsTree.getSelectedItems()) {
        final ArtifactEditorContext context = myArtifactEditor.getContext();
        final List<? extends PackagingElement<?>> elements = item.createElements(context);
        ArtifactUtil.processElementsWithSubstitutions(elements, context, context.getArtifactType(), PackagingElementPath.EMPTY, new PackagingElementProcessor<PackagingElement<?>>() {
          @Override
          public boolean process(@NotNull PackagingElement<?> element, @NotNull PackagingElementPath path) {
            if (element instanceof FileCopyPackagingElement) {
              final VirtualFile file = ((FileCopyPackagingElement)element).findFile();
              if (file != null) {
                final VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(file);
                if (jarRoot != null) {
                  extracted.add(PackagingElementFactory.getInstance().createExtractedDirectory(jarRoot));
                }
              }
            }
            return true;
          }
        });
      }
      myArtifactEditor.getLayoutTreeComponent().putElements(pathForClasses, extracted);
    }
  }
}
