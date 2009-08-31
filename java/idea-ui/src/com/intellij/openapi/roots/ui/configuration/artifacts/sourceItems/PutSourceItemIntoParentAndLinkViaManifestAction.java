package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorEx;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorImpl;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactUtil;
import com.intellij.openapi.roots.ui.configuration.artifacts.ParentElementProcessor;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.impl.elements.ManifestFileUtil;
import com.intellij.packaging.ui.ArtifactEditor;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.ui.PackagingSourceItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class PutSourceItemIntoParentAndLinkViaManifestAction extends AnAction {
  private final SourceItemsTree mySourceItemsTree;
  private final ArtifactEditorEx myArtifactEditor;

  public PutSourceItemIntoParentAndLinkViaManifestAction(SourceItemsTree sourceItemsTree, ArtifactEditorEx artifactEditor) {
    mySourceItemsTree = sourceItemsTree;
    myArtifactEditor = artifactEditor;
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
    for (PackagingSourceItem item : mySourceItemsTree.getSelectedItems()) {
      if (!item.getKindOfProducedElements().containsJarFiles()) {
        enable = false;
        break;
      }
    }
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
    final PackagingEditorContext context = myArtifactEditor.getContext();
    context.ensureRootIsWritable(artifact);
    context.ensureRootIsWritable(parentsInfo.getParentArtifact());
    parentsInfo = findParentAndGrandParent(myArtifactEditor.getArtifact());//find elements under modifiable root
    if (parentsInfo == null) {
      return;
    }

    final CompositePackagingElement<?> grandParent = parentsInfo.getGrandparentElement();
    List<String> classpath = new ArrayList<String>();
    for (PackagingSourceItem item : items) {
      final List<? extends PackagingElement<?>> elements = item.createElements(context);
      grandParent.addOrFindChildren(elements);
      classpath.addAll(ManifestFileUtil.getClasspathForElements(elements, context, artifact.getArtifactType()));
    }
    final ArtifactEditor parentArtifactEditor = context.getOrCreateEditor(parentsInfo.getParentArtifact());
    parentArtifactEditor.addToClasspath(parentsInfo.getParentElement(), classpath);
    ((ArtifactEditorImpl)context.getOrCreateEditor(parentsInfo.getGrandparentArtifact())).rebuildTries();
  }

  private static class ParentElementsInfo {
    private Artifact myParentArtifact;
    private CompositePackagingElement<?> myParentElement;
    private Artifact myGrandparentArtifact;
    private CompositePackagingElement<?> myGrandparentElement;

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
