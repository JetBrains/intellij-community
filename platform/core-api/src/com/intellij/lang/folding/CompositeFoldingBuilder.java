// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.folding;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Used by LanguageFolding class if more than one FoldingBuilder were specified
 * for a particular language.
 *
 * @author Konstantin Bulenkov
 * @see LanguageFolding
 */
public class CompositeFoldingBuilder extends FoldingBuilderEx implements PossiblyDumbAware {
  private final List<? extends FoldingBuilder> myBuilders;

  CompositeFoldingBuilder(List<? extends FoldingBuilder> builders) {
    myBuilders = builders;
  }

  @Override
  public FoldingDescriptor @NotNull [] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
    final List<FoldingDescriptor> descriptors = new ArrayList<>();
    final Set<TextRange> rangesCoveredByDescriptors = new HashSet<>();

    for (FoldingBuilder builder : DumbService.getInstance(root.getProject()).filterByDumbAwareness(myBuilders)) {
      for (FoldingDescriptor descriptor : LanguageFolding.buildFoldingDescriptorsNoPlaceholderCaching(builder, root, document, quick)) {
        if (rangesCoveredByDescriptors.add(descriptor.getRange())) {
          descriptors.add(new FoldingDescriptorWrapper(descriptor, builder));
        }
      }
    }

    return descriptors.toArray(FoldingDescriptor.EMPTY);
  }

  @Override
  public String getPlaceholderText(@NotNull ASTNode node, @NotNull TextRange range) {
    // We reach this when a FoldingDescriptor was created by a regular FoldingBuilder but a composite FoldingBuilder is actually registered
    // for the language
    return node.getText();
  }

  @Override
  public String getPlaceholderText(@NotNull ASTNode node) {
    // We reach this when a FoldingDescriptor was created by a regular FoldingBuilder but a composite FoldingBuilder is actually registered
    // for the language
    return node.getText();
  }

  @Override
  public boolean isCollapsedByDefault(@NotNull ASTNode node) {
    // We reach this when a FoldingDescriptor was created by a regular FoldingBuilder but a composite FoldingBuilder is actually registered
    // for the language
    return false;
  }

  @Override
  public boolean isCollapsedByDefault(@NotNull FoldingDescriptor foldingDescriptor) {
    final FoldingBuilder builder = ((FoldingDescriptorWrapper) foldingDescriptor).myBuilder;
    return mayUseBuilder(foldingDescriptor.getElement(), builder) && builder.isCollapsedByDefault(foldingDescriptor);
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

  @Nullable
  public static FoldingBuilder getOriginalBuilder(@NotNull FoldingDescriptor foldingDescriptor) {
    if (foldingDescriptor instanceof FoldingDescriptorWrapper) {
      return ((FoldingDescriptorWrapper) foldingDescriptor).myBuilder;
    }
    return null;
  }

  private static class FoldingDescriptorWrapper extends FoldingDescriptor {
    @NotNull private final FoldingDescriptor myFoldingDescriptor;
    @NotNull private final FoldingBuilder myBuilder;

    FoldingDescriptorWrapper(@NotNull FoldingDescriptor foldingDescriptor, @NotNull FoldingBuilder builder) {
      super(foldingDescriptor.getElement(),
            foldingDescriptor.getRange(),
            foldingDescriptor.getGroup(),
            foldingDescriptor.getDependencies(),
            foldingDescriptor.isNonExpandable(),
            choosePlaceholderText(foldingDescriptor),
            foldingDescriptor.isCollapsedByDefault());
      myFoldingDescriptor = foldingDescriptor;
      myBuilder = builder;
    }

    private static String choosePlaceholderText(@NotNull FoldingDescriptor foldingDescriptor) {
      String cachedText = foldingDescriptor.getCachedPlaceholderText();
      // Some folding descriptors override the getPlaceholderText() method. If they don't, the default implementation
      // in CompositeFoldingBuilder will return the element text. In this case, we'll need to ensure that the
      // getPlaceholderText() will be delegate to the folding builder, which we achieve by not storing any cached text.
      String textFromGetText = foldingDescriptor.getPlaceholderText();
      boolean placeholderTextIsFallback = Objects.equals(textFromGetText, foldingDescriptor.getElement().getText());
      return placeholderTextIsFallback ? cachedText : textFromGetText;
    }

    @NotNull
    @Override
    public Set<Object> getDependencies() {
      return myFoldingDescriptor.getDependencies();
    }

    @Override
    protected String calcPlaceholderText() {
      ASTNode element = getElement();
      return !mayUseBuilder(element, myBuilder) ? element.getText() :
             myBuilder instanceof FoldingBuilderEx ? ((FoldingBuilderEx)myBuilder).getPlaceholderText(element, getRange())
                                                 : myBuilder.getPlaceholderText(element);
    }

    @Override
    public boolean canBeRemovedWhenCollapsed() {
      return myFoldingDescriptor.canBeRemovedWhenCollapsed();
    }

    @Override
    public void setCanBeRemovedWhenCollapsed(boolean canBeRemovedWhenCollapsed) {
      myFoldingDescriptor.setCanBeRemovedWhenCollapsed(canBeRemovedWhenCollapsed);
    }
  }
}
