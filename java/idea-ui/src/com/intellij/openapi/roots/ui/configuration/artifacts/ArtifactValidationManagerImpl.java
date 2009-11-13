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
package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.CompositePackagingElementNode;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureProblemDescription;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureProblemsHolderImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.ui.ArtifactProblemQuickFix;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
* @author nik
*/
public class ArtifactValidationManagerImpl implements Disposable {
  private ArtifactErrorPanel myErrorPanel;
  private final ArtifactEditorImpl myArtifactEditor;
  private Map<PackagingElementNode<?>, String> myErrorsForNodes = new HashMap<PackagingElementNode<?>, String>();
  private Map<PackagingElement<?>, String> myErrorsForElements = new HashMap<PackagingElement<?>, String>();

  ArtifactValidationManagerImpl(ArtifactEditorImpl artifactEditor) {
    Disposer.register(artifactEditor, this);
    myArtifactEditor = artifactEditor;
    myErrorPanel = new ArtifactErrorPanel(artifactEditor);
  }

  private void addNodeToErrorsWithParents(PackagingElementNode<?> node, String message) {
    if (!myErrorsForNodes.containsKey(node)) {
      myErrorsForNodes.put(node, message);
      final CompositePackagingElementNode parentNode = node.getParentNode();
      if (parentNode != null) {
        addNodeToErrorsWithParents(parentNode, message);
      }
    }
  }

  public void dispose() {
  }

  public JComponent getMainErrorPanel() {
    return myErrorPanel.getMainPanel();
  }

  public void elementAddedToNode(PackagingElementNode<?> node, PackagingElement<?> element) {
    final String message = myErrorsForElements.get(element);
    if (message != null) {
      addNodeToErrorsWithParents(node, message);
    }
  }

  @Nullable
  public String getProblem(PackagingElementNode<?> node) {
    return myErrorsForNodes.get(node);
  }

  public void updateProblems(@Nullable ProjectStructureProblemsHolderImpl holder) {
    myErrorPanel.clearError();
    myErrorsForNodes.clear();
    myErrorsForElements.clear();
    if (holder != null) {
      final List<ProjectStructureProblemDescription> problemDescriptions = holder.getProblemDescriptions();
      if (problemDescriptions != null) {
        for (ProjectStructureProblemDescription description : problemDescriptions) {
          final String message = description.getMessage();
          ArtifactProblemQuickFix quickFix = null;
          if (description instanceof ArtifactProblemDescription) {
            quickFix = ((ArtifactProblemDescription)description).getQuickFix();
            final PackagingElement<?> place = ((ArtifactProblemDescription)description).getPlace();
            if (place != null) {
              final LayoutTree layoutTree = myArtifactEditor.getLayoutTreeComponent().getLayoutTree();
              myErrorsForElements.put(place, message);
              final List<PackagingElementNode<?>> nodes = layoutTree.findNodes(Collections.singletonList(place));
              for (PackagingElementNode<?> node : nodes) {
                addNodeToErrorsWithParents(node, message);
              }
            }
          }
          myErrorPanel.showError(message, quickFix);
        }
      }
    }
    myArtifactEditor.getLayoutTreeComponent().updateTreeNodesPresentation();
  }
}
