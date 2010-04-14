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
package com.intellij.openapi.roots.ui.configuration.artifacts.actions;

import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTreeComponent;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;
import com.intellij.packaging.ui.TreeNodePresentation;
import org.jetbrains.annotations.Nullable;

/**
* @author nik
*/
public class LayoutTreeNavigateAction extends ArtifactEditorNavigateActionBase {
  private final LayoutTreeComponent myLayoutTreeComponent;

  public LayoutTreeNavigateAction(LayoutTreeComponent layoutTreeComponent) {
    super(layoutTreeComponent.getLayoutTree());
    myLayoutTreeComponent = layoutTreeComponent;
  }

  @Override
  @Nullable
  protected TreeNodePresentation getPresentation() {
    PackagingElementNode<?> node = myLayoutTreeComponent.getSelection().getNodeIfSingle();
    return node != null ? node.getElementPresentation() : null;
  }

}
