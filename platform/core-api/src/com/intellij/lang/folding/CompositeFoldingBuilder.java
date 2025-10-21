// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.folding;

import com.intellij.diagnostic.PluginException;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

/**
 * Used by LanguageFolding class if more than one FoldingBuilder were specified
 * for a particular language.
 *
 * @author Konstantin Bulenkov
 * @see LanguageFolding
 */
public class CompositeFoldingBuilder extends FoldingBuilderEx implements PossiblyDumbAware {
  private final @NotNull List<? extends FoldingBuilder> myBuilders;

  CompositeFoldingBuilder(@NotNull List<? extends FoldingBuilder> builders) {
    myBuilders = builders;
  }

  @Override
  public FoldingDescriptor @NotNull [] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
    List<FoldingDescriptor> descriptors = new ArrayList<>();
    Set<TextRange> rangesCoveredByDescriptors = new HashSet<>();

    PsiFile containingFile = PsiUtilCore.getTemplateLanguageFile(root);
    for (FoldingBuilder builder : DumbService.getInstance(root.getProject()).filterByDumbAwareness(myBuilders)) {
      for (FoldingDescriptor descriptor : LanguageFolding.buildFoldingDescriptorsNoPlaceholderCaching(builder, root, document, quick)) {
        PsiElement descriptorPsi = descriptor.getElement().getPsi();
        if (descriptorPsi != null) {
          assertSameFile(containingFile, descriptor, descriptorPsi, builder);
        }
        if (rangesCoveredByDescriptors.add(descriptor.getRange())) {
          descriptors.add(new FoldingDescriptorWrapper(descriptor, builder));
        }
      }
    }

    return descriptors.toArray(FoldingDescriptor.EMPTY_ARRAY);
  }

  public static void assertSameFile(@NotNull PsiFile containingFile,
                                    @NotNull FoldingDescriptor descriptor,
                                    @NotNull PsiElement descriptorPsi,
                                    @NotNull FoldingBuilder foldingBuilder) {
    PsiFile descriptorFile = PsiUtilCore.getTemplateLanguageFile(descriptorPsi);
    if (containingFile != descriptorFile) {
      throw PluginException.createByClass(new IllegalStateException(
        "Folding descriptor " + descriptor + ", containing PSI element " + descriptorPsi + " of " + descriptorPsi.getClass() + "," +
        " provided by " + foldingBuilder + " (" + foldingBuilder.getClass() + ") must belong to the file " +
        containingFile + ", but got: " + descriptorFile), foldingBuilder.getClass());
    }
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
    FoldingBuilder builder = ((FoldingDescriptorWrapper) foldingDescriptor).myBuilder;
    return mayUseBuilder(foldingDescriptor.getElement(), builder) && builder.isCollapsedByDefault(foldingDescriptor);
  }

  @Override
  public boolean keepExpandedOnFirstCollapseAll(@NotNull FoldingDescriptor foldingDescriptor) {
    FoldingBuilder builder = ((FoldingDescriptorWrapper)foldingDescriptor).myBuilder;
    return mayUseBuilder(foldingDescriptor.getElement(), builder) && builder.keepExpandedOnFirstCollapseAll(foldingDescriptor);
  }

  private static boolean mayUseBuilder(@NotNull ASTNode node, @Nullable FoldingBuilder builder) {
    if (builder == null) return false;
    Project project = getProjectByNode(node);
    return project == null || DumbService.getInstance(project).isUsableInCurrentContext(builder);
  }

  private static @Nullable Project getProjectByNode(@NotNull ASTNode node) {
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

  public static @Nullable FoldingBuilder getOriginalBuilder(@NotNull FoldingDescriptor foldingDescriptor) {
    if (foldingDescriptor instanceof FoldingDescriptorWrapper) {
      return ((FoldingDescriptorWrapper) foldingDescriptor).myBuilder;
    }
    return null;
  }

  private static class FoldingDescriptorWrapper extends FoldingDescriptor {
    private final @NotNull FoldingDescriptor myFoldingDescriptor;
    private final @NotNull FoldingBuilder myBuilder;

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
      // in CompositeFoldingBuilder will return the element text. In this case, we will need to ensure that the
      // getPlaceholderText() will be a delegate to the folding builder, which we achieve by not storing any cached text.
      String textFromGetText = foldingDescriptor.getPlaceholderText();
      boolean placeholderTextIsFallback = Objects.equals(textFromGetText, foldingDescriptor.getElement().getText());
      return placeholderTextIsFallback ? cachedText : textFromGetText;
    }

    @Override
    public @NotNull @Unmodifiable Set<Object> getDependencies() {
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
