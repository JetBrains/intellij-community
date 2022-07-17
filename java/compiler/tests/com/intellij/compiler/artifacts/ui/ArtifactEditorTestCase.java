// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.artifacts.ui;

import com.intellij.compiler.artifacts.PackagingElementsTestCase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.ui.configuration.artifacts.*;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.ComplexPackagingElementNode;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.openapi.util.Ref;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;

import javax.swing.tree.TreePath;
import java.util.Arrays;
import java.util.Collections;

public abstract class ArtifactEditorTestCase extends PackagingElementsTestCase {
  protected ArtifactEditorImpl myArtifactEditor;

  @Override
  protected void tearDown() throws Exception {
    myArtifactEditor = null;
    super.tearDown();
  }

  protected void createEditor(Artifact artifact) {
    createEditor(artifact, false);
  }

  protected void showContent(String path) {
    selectNode(path);
    final PackagingElementNode<?> node = myArtifactEditor.getLayoutTreeComponent().getSelection().getNodeIfSingle();
    myArtifactEditor.getSubstitutionParameters().setShowContent(assertInstanceOf(node, ComplexPackagingElementNode.class));
    myArtifactEditor.rebuildTries();
  }

  protected void createEditor(Artifact artifact, final boolean showContent) {
    final ArtifactEditorSettings settings;
    if (showContent) {
      settings = new ArtifactEditorSettings(false, Arrays.asList(PackagingElementFactory.getInstance().getComplexElementTypes()));
    }
    else {
      settings = new ArtifactEditorSettings(false, Collections.emptyList());
    }

    myArtifactEditor = new ArtifactEditorImpl(new MockArtifactsStructureConfigurableContext(), artifact, settings) {
      @Override
      protected ArtifactEditorContextImpl createArtifactEditorContext(ArtifactsStructureConfigurableContext parentContext) {
        return new MockPackagingEditorContext(parentContext, this);
      }
    };
    myArtifactEditor.createMainComponent();
    disposeOnTearDown(myArtifactEditor);
  }

  protected void selectNode(String path){
    final LayoutTreeComponent layoutTreeComponent = myArtifactEditor.getLayoutTreeComponent();
    layoutTreeComponent.getLayoutTree().clearSelection();
    Promise<TreePath> selection = layoutTreeComponent.selectNode(PathUtil.getParentPath(path), PathUtil.getFileName(path));
    TreePath result = PlatformTestUtil.assertPromiseSucceeds(selection);
    assertFalse("Node " + path + " not found, result=" + result, layoutTreeComponent.getSelection().getNodes().isEmpty());
  }

  protected void assertLayout(String expected) {
    assertLayout(myArtifactEditor.getArtifact(), expected);
  }

  protected void applyChanges() {
    ApplicationManager.getApplication().runWriteAction(() -> {
      myArtifactEditor.apply();
      ((MockArtifactsStructureConfigurableContext)myArtifactEditor.getContext().getParent()).commitModel();
    });
  }

  protected static void runAction(final Runnable action, boolean confirmationExpected) {
    final Ref<Boolean> dialogShown = Ref.create(false);
    final TestDialog oldDialog = TestDialogManager.setTestDialog(new TestDialog() {
      @Override
      public int show(@NotNull String message) {
        dialogShown.set(true);
        return 0;
      }
    });

    try {
      action.run();
    }
    finally {
      TestDialogManager.setTestDialog(oldDialog);
    }

    if (confirmationExpected) {
      assertTrue("Dialog wasn't shown", dialogShown.get());
    }
    else {
      assertFalse("Dialog was shown", dialogShown.get());
    }
  }
}
