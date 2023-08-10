// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.module.Module;
import com.intellij.platform.workspace.storage.EntityReference;
import com.intellij.util.indexing.IndexingBundle;
import com.intellij.util.indexing.roots.origin.IndexingRootHolder;
import com.intellij.util.indexing.roots.origin.ModuleAwareContentEntityOrigin;
import com.intellij.util.indexing.roots.origin.ModuleAwareContentEntityOriginImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ModuleAwareContentEntityIteratorImpl extends RootHolderIteratorBase {

  private final Module module;

  public ModuleAwareContentEntityIteratorImpl(@NotNull Module module,
                                              @NotNull EntityReference<?> entityReference,
                                              @NotNull IndexingRootHolder roots,
                                              @Nullable IndexableIteratorPresentation presentation) {
    super(entityReference, roots, presentation != null ? presentation : IndexableIteratorPresentation.create(
      "Content roots from module " + module.getName() + " from entity (" + roots.getRootsDebugStr() + ")",
      IndexingBundle.message("indexable.files.provider.indexing.content"),
      IndexingBundle.message("indexable.files.provider.scanning.content")));
    this.module = module;
  }

  @NotNull
  @Override
  public ModuleAwareContentEntityOrigin getOrigin() {
    return new ModuleAwareContentEntityOriginImpl(module, entityReference, roots);
  }
}
