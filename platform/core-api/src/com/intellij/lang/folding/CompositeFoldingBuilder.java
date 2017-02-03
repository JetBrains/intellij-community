/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Used by LanguageFolding class if more than one FoldingBuilder were specified
 * for a particular language.
 *
 * @author Konstantin Bulenkov
 * @see LanguageFolding
 * @since 9.0
 */
public class CompositeFoldingBuilder extends FoldingBuilderEx implements PossiblyDumbAware {
  public static final Key<FoldingBuilder> FOLDING_BUILDER = new Key<FoldingBuilder>("FOLDING_BUILDER");
  private final List<FoldingBuilder> myBuilders;

  CompositeFoldingBuilder(List<FoldingBuilder> builders) {
    myBuilders = builders;
  }

  @NotNull
  public List<FoldingBuilder> getAllBuilders() {
    return Collections.unmodifiableList(myBuilders);
  }

  @NotNull
  public FoldingDescriptor[] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
    final List<FoldingDescriptor> descriptors = new ArrayList<FoldingDescriptor>();
    final Set<TextRange> rangesCoveredByDescriptors = ContainerUtil.newHashSet();

    for (FoldingBuilder builder : DumbService.getInstance(root.getProject()).filterByDumbAwareness(myBuilders)) {
      for (FoldingDescriptor descriptor : LanguageFolding.buildFoldingDescriptors(builder, root, document, quick)) {
        if (rangesCoveredByDescriptors.add(descriptor.getRange())) {
          descriptor.getElement().putUserData(FOLDING_BUILDER, builder);
          descriptors.add(descriptor);
        }
      }
    }

    return descriptors.toArray(new FoldingDescriptor[descriptors.size()]);
  }

  @Override
  public String getPlaceholderText(@NotNull ASTNode node, @NotNull TextRange range) {
    final FoldingBuilder builder = node.getUserData(FOLDING_BUILDER);
    return !mayUseBuilder(node, builder) ? node.getText() :
           builder instanceof FoldingBuilderEx ? ((FoldingBuilderEx)builder).getPlaceholderText(node, range)
                                               : builder.getPlaceholderText(node);
  }

  @Override
  public String getPlaceholderText(@NotNull ASTNode node) {
    final FoldingBuilder builder = node.getUserData(FOLDING_BUILDER);
    return !mayUseBuilder(node, builder) ? node.getText() : builder.getPlaceholderText(node);
  }

  public boolean isCollapsedByDefault(@NotNull ASTNode node) {
    final FoldingBuilder builder = node.getUserData(FOLDING_BUILDER);
    return mayUseBuilder(node, builder) && builder.isCollapsedByDefault(node);
  }

  private static boolean mayUseBuilder(@NotNull ASTNode node, @Nullable FoldingBuilder builder) {
    if (builder == null) return false;
    if (DumbService.isDumbAware(builder)) return true;

    Project project = getProjectByNode(node);
    return project == null || !DumbService.isDumb(project);
  }

  @Nullable
  private static Project getProjectByNode(@NotNull ASTNode node) {
    PsiElement psi = node.getPsi();
    if (psi == null) {
      ASTNode parent = node.getTreeParent();
      psi = parent == null ? null : parent.getPsi();
    }
    return psi == null ? null : psi.getProject();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + myBuilders;
  }

  @Override
  public boolean isDumbAware() {
    for (FoldingBuilder builder : myBuilders) {
      if (DumbService.isDumbAware(builder)) {
        return true;
      }
    }
    return false;
  }
}
