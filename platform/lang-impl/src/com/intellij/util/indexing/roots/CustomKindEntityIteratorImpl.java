// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.platform.workspace.storage.EntityReference;
import com.intellij.util.indexing.IndexingBundle;
import com.intellij.util.indexing.roots.origin.CustomKindEntityOrigin;
import com.intellij.util.indexing.roots.origin.CustomKindEntityOriginImpl;
import com.intellij.util.indexing.roots.origin.IndexingRootHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public final class CustomKindEntityIteratorImpl extends RootHolderIteratorBase {

  public CustomKindEntityIteratorImpl(@NotNull EntityReference<?> entityReference,
                                      @NotNull IndexingRootHolder roots,
                                      @Nullable IndexableIteratorPresentation presentation) {
    super(entityReference, roots, presentation != null
                                  ? presentation
                                  : IndexableIteratorPresentation.create(
                                    "Custom kind roots from entity (" + roots.getRootsDebugStr() + ")",
                                    IndexingBundle.message("indexable.files.provider.indexing.additional.dependencies"),
                                    IndexingBundle.message("indexable.files.provider.scanning.additional.dependencies")),
          true);
  }

  @NotNull
  @Override
  public CustomKindEntityOrigin getOrigin() {
    return new CustomKindEntityOriginImpl(entityReference, roots);
  }
}
