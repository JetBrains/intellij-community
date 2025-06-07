// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  public @Nullable Collection<ArtifactProblemDescription> getProblems(PackagingElementNode<?> node) {
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
          final String message = description.getMessage();
          List<? extends ConfigurationErrorQuickFix> quickFixes = Collections.emptyList();
          if (description instanceof ArtifactProblemDescription artifactProblem) {
            quickFixes = artifactProblem.getFixes();
            if (artifactProblem.getPathToPlace() != null) {
              myProblems.add(artifactProblem);
              showProblemInTree(artifactProblem);
            }
          }
          myErrorPanel.showError(message, description.getSeverity(), quickFixes);
        }
      }
    }
    myArtifactEditor.getLayoutTreeComponent().updateTreeNodesPresentation();
  }

  private void showProblemInTree(ArtifactProblemDescription problem) {
    PackagingElementNode<?> node = myArtifactEditor.getLayoutTreeComponent().getRootNode();
    final List<PackagingElement<?>> pathToPlace = problem.getPathToPlace();
    if (node != null && pathToPlace != null) {
      List<PackagingElementNode<?>> nodes = node.getNodesByPath(pathToPlace.subList(1, pathToPlace.size()));
      for (PackagingElementNode<?> elementNode : nodes) {
        myProblemsForNodes.put(elementNode, problem);
      }
    }
  }
}
