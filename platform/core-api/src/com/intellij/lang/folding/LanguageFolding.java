// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.folding;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.psi.PsiElement;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.SlowOperations;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.List;

public final class LanguageFolding extends LanguageExtension<FoldingBuilder> {
  public static final ExtensionPointName<KeyedLazyInstance<FoldingBuilder>> EP_NAME = ExtensionPointName.create("com.intellij.lang.foldingBuilder");
  public static final LanguageFolding INSTANCE = new LanguageFolding();

  private static final Logger LOG = Logger.getInstance(LanguageFolding.class);

  private LanguageFolding() {
    super(EP_NAME);
  }

  /**
   * This method is left to preserve binary compatibility.
   */
  @SuppressWarnings("RedundantMethodOverride")
  @Override
  public FoldingBuilder forLanguage(@NotNull Language l) {
    return super.forLanguage(l);
  }

  @Override
  protected FoldingBuilder findForLanguage(@NotNull Language l) {
    List<FoldingBuilder> extensions = allForLanguageOrAny(l);
    if (extensions.isEmpty()) {
      return null;
    }
    else if (extensions.size() == 1) {
      return extensions.get(0);
    }
    else {
      return new CompositeFoldingBuilder(extensions);
    }
  }

  /**
   * Only queries base language results if there are no extensions for originally requested language.
   */
  @Override
  public @NotNull @Unmodifiable List<FoldingBuilder> allForLanguage(@NotNull Language language) {
    for (Language l = language; l != null; l = l.getBaseLanguage()) {
      List<FoldingBuilder> extensions = forKey(l);
      if (!extensions.isEmpty()) {
        return extensions;
      }
    }
    return Collections.emptyList();
  }

  public static FoldingDescriptor @NotNull [] buildFoldingDescriptors(@Nullable FoldingBuilder builder,
                                                                      @NotNull PsiElement root,
                                                                      @NotNull Document document,
                                                                      boolean quick) {
    FoldingDescriptor[] descriptors = buildFoldingDescriptorsNoPlaceholderCaching(builder, root, document, quick);
    for (FoldingDescriptor descriptor : descriptors) {
      descriptor.setPlaceholderText(descriptor.getPlaceholderText()); // cache placeholder text
    }
    return descriptors;
  }

  static FoldingDescriptor @NotNull [] buildFoldingDescriptorsNoPlaceholderCaching(@Nullable FoldingBuilder builder,
                                                                                   @NotNull PsiElement root,
                                                                                   @NotNull Document document,
                                                                                   boolean quick) {
    SlowOperations.assertSlowOperationsAreAllowed();
    try {
      if (builder != null && !DumbService.getInstance(root.getProject()).isUsableInCurrentContext(builder)) {
        return FoldingDescriptor.EMPTY_ARRAY;
      }

      if (builder instanceof FoldingBuilderEx) {
        return ((FoldingBuilderEx)builder).buildFoldRegions(root, document, quick);
      }
      final ASTNode astNode = root.getNode();
      if (astNode == null || builder == null) {
        return FoldingDescriptor.EMPTY_ARRAY;
      }

      return builder.buildFoldRegions(astNode, document);
    }
    catch (IndexNotReadyException e) {
      return FoldingDescriptor.EMPTY_ARRAY;
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      LOG.error(e);
      return FoldingDescriptor.EMPTY_ARRAY;
    }
  }
}
