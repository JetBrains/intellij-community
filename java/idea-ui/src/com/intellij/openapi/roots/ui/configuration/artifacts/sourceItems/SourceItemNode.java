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
package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorEx;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingSourceItem;
import com.intellij.ui.treeStructure.SimpleTree;
import org.jetbrains.annotations.NotNull;

import java.awt.event.InputEvent;
import java.util.Collections;

/**
 * @author nik
 */
public class SourceItemNode extends SourceItemNodeBase {
  private final PackagingSourceItem mySourceItem;

  public SourceItemNode(ArtifactEditorContext context, NodeDescriptor parentDescriptor, PackagingSourceItem sourceItem, ArtifactEditorEx artifactEditor) {
    super(context, parentDescriptor, sourceItem.createPresentation(context), artifactEditor);
    mySourceItem = sourceItem;
  }

  @NotNull
  @Override
  public Object[] getEqualityObjects() {
    return new Object[]{mySourceItem};
  }

  @Override
  public void handleDoubleClickOrEnter(SimpleTree tree, InputEvent inputEvent) {
    if (mySourceItem.isProvideElements() && getChildren().length == 0) {
      getArtifactEditor().getLayoutTreeComponent().putIntoDefaultLocations(Collections.singletonList(mySourceItem));
    }
  }

  @Override
  public PackagingSourceItem getSourceItem() {
    return mySourceItem;
  }
}
