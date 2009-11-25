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
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureProblemDescription;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureProblemsHolderImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.util.Pair;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.ui.ArtifactProblemQuickFix;
import org.jetbrains.annotations.NotNull;
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
  private ArtifactErrorPanel myErrorPanel;
  private final ArtifactEditorImpl myArtifactEditor;
  private MultiValuesMap<PackagingElementNode<?>, String> myErrorsForNodes = new MultiValuesMap<PackagingElementNode<?>, String>(true);
  private List<Pair<String, List<PackagingElement<?>>>> myProblems = new ArrayList<Pair<String, List<PackagingElement<?>>>>();

  ArtifactValidationManagerImpl(ArtifactEditorImpl artifactEditor) {
    Disposer.register(artifactEditor, this);
    myArtifactEditor = artifactEditor;
    myErrorPanel = new ArtifactErrorPanel(artifactEditor);
  }

  public void dispose() {
  }

  public JComponent getMainErrorPanel() {
    return myErrorPanel.getMainPanel();
  }

  public void onNodesAdded() {
    for (Pair<String, List<PackagingElement<?>>> problem : myProblems) {
      registerProblem(problem.getFirst(), problem.getSecond());
    }
  }

  private void registerProblem(@NotNull PackagingElementNode<?> node, @NotNull String message) {
    myErrorsForNodes.put(node, message);
  }

  @Nullable
  public Collection<String> getProblems(PackagingElementNode<?> node) {
    return myErrorsForNodes.get(node);
  }

  public void updateProblems(@Nullable ProjectStructureProblemsHolderImpl holder) {
    myErrorPanel.clearError();
    myErrorsForNodes.clear();
    myProblems.clear();
    if (holder != null) {
      final List<ProjectStructureProblemDescription> problemDescriptions = holder.getProblemDescriptions();
      if (problemDescriptions != null) {
        for (ProjectStructureProblemDescription description : problemDescriptions) {
          final String message = description.getMessage();
          List<ArtifactProblemQuickFix> quickFix = Collections.emptyList();
          if (description instanceof ArtifactProblemDescription) {
            quickFix = ((ArtifactProblemDescription)description).getQuickFixes();
            final List<PackagingElement<?>> pathToPlace = ((ArtifactProblemDescription)description).getPathToPlace();
            if (pathToPlace != null) {
              myProblems.add(Pair.create(message, pathToPlace));
              registerProblem(message, pathToPlace);
            }
          }
          myErrorPanel.showError(message, quickFix);
        }
      }
    }
    myArtifactEditor.getLayoutTreeComponent().updateTreeNodesPresentation();
  }

  private void registerProblem(String message, List<PackagingElement<?>> pathToPlace) {
    final LayoutTree layoutTree = myArtifactEditor.getLayoutTreeComponent().getLayoutTree();
    PackagingElementNode<?> node = layoutTree.getRootPackagingNode();
    if (node != null) {
      List<PackagingElementNode<?>> nodes = node.getNodesByPath(pathToPlace.subList(1, pathToPlace.size()));
      for (PackagingElementNode<?> elementNode : nodes) {
        registerProblem(elementNode, message);
      }
    }
  }
}
