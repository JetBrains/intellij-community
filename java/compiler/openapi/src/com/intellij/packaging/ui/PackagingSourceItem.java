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
package com.intellij.packaging.ui;

import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementOutputKind;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public abstract class PackagingSourceItem {
  private final boolean myProvideElements;

  protected PackagingSourceItem() {
    this(true);
  }

  protected PackagingSourceItem(boolean provideElements) {
    myProvideElements = provideElements;
  }

  @Override
  public abstract boolean equals(Object obj);

  @Override
  public abstract int hashCode();

  public abstract SourceItemPresentation createPresentation(@NotNull ArtifactEditorContext context);

  @NotNull
  public abstract List<? extends PackagingElement<?>> createElements(@NotNull ArtifactEditorContext context);

  public boolean isProvideElements() {
    return myProvideElements;
  }

  @NotNull
  public PackagingElementOutputKind getKindOfProducedElements() {
    return PackagingElementOutputKind.OTHER;
  }
}
