// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems.actions;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorEx;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorImpl;
import com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems.SourceItemsTree;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.impl.artifacts.ParentElementProcessor;
import com.intellij.packaging.impl.elements.ManifestFileUtil;
import com.intellij.packaging.ui.ArtifactEditor;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingSourceItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PutSourceItemIntoParentAndLinkViaManifestAction extends PutIntoDefaultLocationActionBase {
  public PutSourceItemIntoParentAndLinkViaManifestAction(SourceItemsTree sourceItemsTree, ArtifactEditorEx artifactEditor) {
    super(sourceItemsTree, artifactEditor);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final Artifact artifact = myArtifactEditor.getArtifact();

    final ParentElementsInfo parentInfo = findParentAndGrandParent(artifact);
    if (parentInfo != null) {
      presentation.setText(JavaUiBundle.message("action.text.put.into.0.and.link.via.manifest", parentInfo.getGrandparentArtifact().getName()));
    }

    boolean enable = parentInfo != null;
    boolean isProvideElements = false;
    for (PackagingSourceItem item : mySourceItemsTree.getSelectedItems()) {
      isProvideElements |= item.isProvideElements();
      if (!item.getKindOfProducedElements().containsJarFiles()) {
        enable = false;
        break;
      }
    }
    enable &= isProvideElements;
    presentation.setEnabledAndVisible(enable);
  }

  @Nullable
  private ParentElementsInfo findParentAndGrandParent(Artifact artifact) {
    final Ref<ParentElementsInfo> result = Ref.create(null);
    ArtifactUtil.processParents(artifact, myArtifactEditor.getContext(), new ParentElementProcessor() {
      @Override
      public boolean process(@NotNull CompositePackagingElement<?> element,
                             @NotNull List<? extends Pair<Artifact, CompositePackagingElement<?>>> parents,
                             @NotNull Artifact artifact) {
        if (parents.size() == 1) {
          final Pair<Artifact, CompositePackagingElement<?>> parent = parents.get(0);
          result.set(new ParentElementsInfo(parent.getFirst(), parent.getSecond(), artifact, element));
          return false;
        }
        return true;
      }
    }, 1);

    return result.get();
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final List<PackagingSourceItem> items = mySourceItemsTree.getSelectedItems();
    ParentElementsInfo parentsInfo = findParentAndGrandParent(myArtifactEditor.getArtifact());
    if (parentsInfo == null) {
      return;
    }

    final Artifact artifact = parentsInfo.getGrandparentArtifact();
    final ArtifactEditorContext context = myArtifactEditor.getContext();
    //todo[nik] improve
    final Runnable emptyRunnable = EmptyRunnable.getInstance();
    context.editLayout(artifact, emptyRunnable);
    context.editLayout(parentsInfo.getParentArtifact(), emptyRunnable);
    parentsInfo = findParentAndGrandParent(myArtifactEditor.getArtifact());//find elements under modifiable root
    if (parentsInfo == null) {
      return;
    }

    final CompositePackagingElement<?> grandParent = parentsInfo.getGrandparentElement();
    final List<String> classpath = new ArrayList<>();
    context.editLayout(artifact, () -> {
      for (PackagingSourceItem item : items) {
        final List<? extends PackagingElement<?>> elements = item.createElements(context);
        grandParent.addOrFindChildren(elements);
        classpath.addAll(ManifestFileUtil.getClasspathForElements(elements, context, artifact.getArtifactType()));
      }
    });

    final ArtifactEditor parentArtifactEditor = context.getOrCreateEditor(parentsInfo.getParentArtifact());
    parentArtifactEditor.addToClasspath(parentsInfo.getParentElement(), classpath);
    ((ArtifactEditorImpl)context.getOrCreateEditor(parentsInfo.getGrandparentArtifact())).rebuildTries();
  }

  private static final class ParentElementsInfo {
    private final Artifact myParentArtifact;
    private final CompositePackagingElement<?> myParentElement;
    private final Artifact myGrandparentArtifact;
    private final CompositePackagingElement<?> myGrandparentElement;

    private ParentElementsInfo(Artifact parentArtifact,
                               CompositePackagingElement<?> parentElement,
                               Artifact grandparentArtifact,
                               CompositePackagingElement<?> grandparentElement) {
      myParentArtifact = parentArtifact;
      myParentElement = parentElement;
      myGrandparentArtifact = grandparentArtifact;
      myGrandparentElement = grandparentElement;
    }

    public Artifact getParentArtifact() {
      return myParentArtifact;
    }

    public CompositePackagingElement<?> getParentElement() {
      return myParentElement;
    }

    public Artifact getGrandparentArtifact() {
      return myGrandparentArtifact;
    }

    public CompositePackagingElement<?> getGrandparentElement() {
      return myGrandparentElement;
    }
  }
}
