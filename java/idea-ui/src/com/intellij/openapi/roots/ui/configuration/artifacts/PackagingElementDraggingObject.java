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

import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.ui.ArtifactEditorContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public abstract class PackagingElementDraggingObject {
  private PackagingElementNode<?> myTargetNode;
  private CompositePackagingElement<?> myTargetElement;

  public abstract List<PackagingElement<?>> createPackagingElements(ArtifactEditorContext context);

  public void setTargetNode(PackagingElementNode<?> targetNode) {
    myTargetNode = targetNode;
  }

  public PackagingElementNode<?> getTargetNode() {
    return myTargetNode;
  }

  public CompositePackagingElement<?> getTargetElement() {
    return myTargetElement;
  }

  public void setTargetElement(CompositePackagingElement<?> targetElement) {
    myTargetElement = targetElement;
  }

  public boolean checkCanDrop() {
    return true;
  }

  public void beforeDrop() {
  }

  public boolean canDropInto(@NotNull PackagingElementNode node) {
    return true;
  }
}
