// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.module.Module;
import com.intellij.platform.workspace.storage.EntityPointer;
import com.intellij.util.indexing.IndexingBundle;
import com.intellij.util.indexing.roots.origin.IndexingRootHolder;
import com.intellij.util.indexing.roots.origin.ModuleAwareContentEntityOrigin;
import com.intellij.util.indexing.roots.origin.ModuleAwareContentEntityOriginImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ModuleAwareContentEntityIteratorImpl extends RootHolderIteratorBase {

  private final Module module;

  public ModuleAwareContentEntityIteratorImpl(@NotNull Module module,
                                              @NotNull EntityPointer<?> entityPointer,
                                              @NotNull IndexingRootHolder roots) {
    super(entityPointer, roots, IndexableIteratorPresentation.create(
      "Content roots from module " + module.getName() + " from entity (" + roots.getDebugDescription() + ")",
      IndexingBundle.message("indexable.files.provider.indexing.content"),
      IndexingBundle.message("indexable.files.provider.scanning.content")));
    this.module = module;
  }

  @Override
  public @NotNull ModuleAwareContentEntityOrigin getOrigin() {
    return new ModuleAwareContentEntityOriginImpl(module, myEntityPointer, roots);
  }
}
