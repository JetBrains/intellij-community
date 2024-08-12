// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.platform.workspace.storage.EntityPointer;
import com.intellij.util.indexing.IndexingBundle;
import com.intellij.util.indexing.roots.origin.CustomKindEntityOrigin;
import com.intellij.util.indexing.roots.origin.CustomKindEntityOriginImpl;
import com.intellij.util.indexing.roots.origin.IndexingRootHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public final class CustomKindEntityIteratorImpl extends RootHolderIteratorBase {

  public CustomKindEntityIteratorImpl(@NotNull EntityPointer<?> entityPointer,
                                      @NotNull IndexingRootHolder roots,
                                      @Nullable IndexableIteratorPresentation presentation) {
    super(entityPointer, roots, presentation != null
                                  ? presentation
                                  : IndexableIteratorPresentation.create(
                                    "Custom kind roots from entity (" + roots.getDebugDescription() + ")",
                                    IndexingBundle.message("indexable.files.provider.indexing.additional.dependencies"),
                                    IndexingBundle.message("indexable.files.provider.scanning.additional.dependencies")),
          true);
  }

  @Override
  public @NotNull CustomKindEntityOrigin getOrigin() {
    return new CustomKindEntityOriginImpl(myEntityPointer, roots);
  }
}
