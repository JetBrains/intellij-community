// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.module.Module;
import com.intellij.platform.workspace.storage.EntityReference;
import com.intellij.util.indexing.customizingIteration.ModuleAwareContentEntityIterator;
import com.intellij.util.indexing.roots.origin.IndexingRootHolder;
import com.intellij.util.indexing.roots.origin.ModuleAwareContentEntityOrigin;
import com.intellij.util.indexing.roots.origin.ModuleAwareContentEntityOriginImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ModuleAwareContentEntityIteratorImpl extends RootHolderIteratorBase implements ModuleAwareContentEntityIterator {

  private final Module module;

  public ModuleAwareContentEntityIteratorImpl(@NotNull Module module,
                                              @NotNull EntityReference<?> entityReference,
                                              @NotNull IndexingRootHolder roots,
                                              @Nullable IndexableIteratorPresentation presentation) {
    super(entityReference, roots, presentation);
    this.module = module;
  }

  @Override
  protected @NotNull IndexableIteratorPresentation createDefaultPresentation(@NotNull IndexingRootHolder roots) {
    return IndexableIteratorPresentation.create(
      "Content roots from module " + module.getName() + " from entity (" + roots.getRootsDebugStr() + ")",
      ModuleAwareContentEntityIterator.super.getIndexingProgressText(),
      ModuleAwareContentEntityIterator.super.getRootsScanningProgressText());
  }

  @NotNull
  @Override
  public ModuleAwareContentEntityOrigin getOrigin() {
    return new ModuleAwareContentEntityOriginImpl(module, entityReference, roots);
  }
}
