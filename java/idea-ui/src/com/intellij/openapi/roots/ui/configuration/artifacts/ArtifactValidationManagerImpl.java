package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.CompositePackagingElementNode;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;
import com.intellij.openapi.util.Disposer;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.impl.ui.ArtifactValidationManagerBase;
import com.intellij.packaging.ui.ArtifactProblemQuickFix;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
* @author nik
*/
public class ArtifactValidationManagerImpl extends ArtifactValidationManagerBase implements Disposable {
  private MergingUpdateQueue myValidationQueue;
  private ArtifactErrorPanel myErrorPanel;
  private final ArtifactEditorImpl myArtifactEditor;
  private Map<PackagingElementNode<?>, String> myErrorsForNodes = new HashMap<PackagingElementNode<?>, String>();
  private Map<PackagingElement<?>, String> myErrorsForElements = new HashMap<PackagingElement<?>, String>();

  ArtifactValidationManagerImpl(ArtifactEditorImpl artifactEditor) {
    super(artifactEditor.getContext());
    Disposer.register(artifactEditor, this);
    myArtifactEditor = artifactEditor;
    myErrorPanel = new ArtifactErrorPanel(artifactEditor);
    final JComponent mainComponent = artifactEditor.getMainComponent();
    myValidationQueue = new MergingUpdateQueue("ArtifactValidation", 300, false, mainComponent, this, mainComponent);
  }

  private void runValidation() {
    myErrorPanel.clearError();
    myErrorsForNodes.clear();
    myErrorsForElements.clear();

    myArtifactEditor.getLayoutTreeComponent().saveElementProperties();
    final Artifact artifact = myArtifactEditor.getArtifact();
    artifact.getArtifactType().checkRootElement(myArtifactEditor.getRootElement(), artifact, this);

    myArtifactEditor.getLayoutTreeComponent().updateTreeNodesPresentation();
  }

  public void registerProblem(@NotNull String message, @Nullable PackagingElement<?> place, @Nullable ArtifactProblemQuickFix quickFix) {
    if (place != null) {
      final LayoutTree layoutTree = myArtifactEditor.getLayoutTreeComponent().getLayoutTree();
      myErrorsForElements.put(place, message);
      final List<PackagingElementNode<?>> nodes = layoutTree.findNodes(Collections.singletonList(place));
      for (PackagingElementNode<?> node : nodes) {
        addNodeToErrorsWithParents(node, message);
      }
    }
    myErrorPanel.showError(message, quickFix);
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

  public void queueValidation() {
    myValidationQueue.queue(new Update("validate") {
      public void run() {
        runValidation();
      }
    });
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
}
