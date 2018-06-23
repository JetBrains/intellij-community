// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  public static final Key<FoldingBuilder> FOLDING_BUILDER = new Key<>("FOLDING_BUILDER");
  private final List<FoldingBuilder> myBuilders;

  CompositeFoldingBuilder(List<FoldingBuilder> builders) {
    myBuilders = builders;
  }

  @Override
  @NotNull
  public FoldingDescriptor[] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
    final List<FoldingDescriptor> descriptors = new ArrayList<>();
    final Set<TextRange> rangesCoveredByDescriptors = ContainerUtil.newHashSet();

    for (FoldingBuilder builder : DumbService.getInstance(root.getProject()).filterByDumbAwareness(myBuilders)) {
      for (FoldingDescriptor descriptor : LanguageFolding.buildFoldingDescriptors(builder, root, document, quick)) {
        if (rangesCoveredByDescriptors.add(descriptor.getRange())) {
          descriptor.getElement().putUserData(FOLDING_BUILDER, builder);
          descriptors.add(descriptor);
        }
      }
    }

    return descriptors.toArray(FoldingDescriptor.EMPTY);
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

  @Override
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
