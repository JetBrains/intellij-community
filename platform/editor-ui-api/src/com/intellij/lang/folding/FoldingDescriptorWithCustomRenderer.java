/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.lang.folding;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class FoldingDescriptorWithCustomRenderer extends FoldingDescriptor {
  private final Inlay.Renderer myRenderer;

  public FoldingDescriptorWithCustomRenderer(@NotNull PsiElement element,
                                             @NotNull TextRange range,
                                             Inlay.Renderer renderer) {
    super(element, range);
    myRenderer = renderer;
  }

  public FoldingDescriptorWithCustomRenderer(@NotNull ASTNode node,
                                             @NotNull TextRange range,
                                             @Nullable FoldingGroup group,
                                             Set<Object> dependencies,
                                             boolean neverExpands,
                                             @NotNull Inlay.Renderer renderer) {
    super(node, range, group, dependencies, neverExpands);
    myRenderer = renderer;
  }

  @Nullable
  @Override
  public String getPlaceholderText() {
    return null;
  }

  public Inlay.Renderer getRenderer() {
    return myRenderer;
  }
}
