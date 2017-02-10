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
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ConfigurationErrorQuickFix;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureProblemDescription;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureProblemsHolderImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.packaging.elements.PackagingElement;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
* @author nik
*/
public class ArtifactValidationManagerImpl implements Disposable {
  private final ArtifactErrorPanel myErrorPanel;
  private final ArtifactEditorImpl myArtifactEditor;
  private final MultiValuesMap<PackagingElementNode<?>, ArtifactProblemDescription> myProblemsForNodes = new MultiValuesMap<>(true);
  private final List<ArtifactProblemDescription> myProblems = new ArrayList<>();

  ArtifactValidationManagerImpl(ArtifactEditorImpl artifactEditor) {
    Disposer.register(artifactEditor, this);
    myArtifactEditor = artifactEditor;
    myErrorPanel = new ArtifactErrorPanel(artifactEditor);
  }

  @Override
  public void dispose() {
  }

  public JComponent getMainErrorPanel() {
    return myErrorPanel.getMainPanel();
  }

  public void onNodesAdded() {
    for (ArtifactProblemDescription problem : myProblems) {
      showProblemInTree(problem);
    }
  }

  @Nullable
  public Collection<ArtifactProblemDescription> getProblems(PackagingElementNode<?> node) {
    return myProblemsForNodes.get(node);
  }

  public void updateProblems(@Nullable ProjectStructureProblemsHolderImpl holder) {
    myErrorPanel.clearError();
    myProblemsForNodes.clear();
    myProblems.clear();
    if (holder != null) {
      final List<ProjectStructureProblemDescription> problemDescriptions = holder.getProblemDescriptions();
      if (problemDescriptions != null) {
        for (ProjectStructureProblemDescription description : problemDescriptions) {
          final String message = description.getMessage(false);
          List<? extends ConfigurationErrorQuickFix> quickFixes = Collections.emptyList();
          if (description instanceof ArtifactProblemDescription) {
            final ArtifactProblemDescription artifactProblem = (ArtifactProblemDescription)description;
            quickFixes = artifactProblem.getFixes();
            if (artifactProblem.getPathToPlace() != null) {
              myProblems.add(artifactProblem);
              showProblemInTree(artifactProblem);
            }
          }
          myErrorPanel.showError(message, quickFixes);
        }
      }
    }
    myArtifactEditor.getLayoutTreeComponent().updateTreeNodesPresentation();
  }

  private void showProblemInTree(ArtifactProblemDescription problem) {
    final LayoutTree layoutTree = myArtifactEditor.getLayoutTreeComponent().getLayoutTree();
    PackagingElementNode<?> node = layoutTree.getRootPackagingNode();
    final List<PackagingElement<?>> pathToPlace = problem.getPathToPlace();
    if (node != null && pathToPlace != null) {
      List<PackagingElementNode<?>> nodes = node.getNodesByPath(pathToPlace.subList(1, pathToPlace.size()));
      for (PackagingElementNode<?> elementNode : nodes) {
        myProblemsForNodes.put(elementNode, problem);
      }
    }
  }
}
