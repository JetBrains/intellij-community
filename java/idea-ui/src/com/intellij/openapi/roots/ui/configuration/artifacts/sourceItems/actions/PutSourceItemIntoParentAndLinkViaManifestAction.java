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
package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems.actions;

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

/**
 * @author nik
 */
public class PutSourceItemIntoParentAndLinkViaManifestAction extends PutIntoDefaultLocationActionBase {
  public PutSourceItemIntoParentAndLinkViaManifestAction(SourceItemsTree sourceItemsTree, ArtifactEditorEx artifactEditor) {
    super(sourceItemsTree, artifactEditor);
  }

  @Override
  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final Artifact artifact = myArtifactEditor.getArtifact();

    final ParentElementsInfo parentInfo = findParentAndGrandParent(artifact);
    if (parentInfo != null) {
      presentation.setText("Put Into '" + parentInfo.getGrandparentArtifact().getName() + "' and link via manifest");
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
    presentation.setVisible(enable);
    presentation.setEnabled(enable);
  }

  @Nullable 
  private ParentElementsInfo findParentAndGrandParent(Artifact artifact) {
    final Ref<ParentElementsInfo> result = Ref.create(null);
    ArtifactUtil.processParents(artifact, myArtifactEditor.getContext(), new ParentElementProcessor() {
      @Override
      public boolean process(@NotNull CompositePackagingElement<?> element,
                             @NotNull List<Pair<Artifact,CompositePackagingElement<?>>> parents,
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
  public void actionPerformed(AnActionEvent e) {
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

  private static class ParentElementsInfo {
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
