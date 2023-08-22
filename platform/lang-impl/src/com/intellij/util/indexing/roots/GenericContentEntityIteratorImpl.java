// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.platform.workspace.storage.EntityReference;
import com.intellij.util.indexing.IndexingBundle;
import com.intellij.util.indexing.roots.origin.GenericContentEntityOrigin;
import com.intellij.util.indexing.roots.origin.GenericContentEntityOriginImpl;
import com.intellij.util.indexing.roots.origin.IndexingRootHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public final class GenericContentEntityIteratorImpl extends RootHolderIteratorBase {

  public GenericContentEntityIteratorImpl(@NotNull EntityReference<?> entityReference,
                                          @NotNull IndexingRootHolder roots,
                                          @Nullable IndexableIteratorPresentation presentation) {
    super(entityReference, roots, presentation != null
                                  ? presentation
                                  : IndexableIteratorPresentation.create(
                                    "Module unaware content roots from entity (" + roots.getRootsDebugStr() + ")",
                                    IndexingBundle.message("indexable.files.provider.indexing.content"),
                                    IndexingBundle.message("indexable.files.provider.scanning.content")));
  }

  @NotNull
  @Override
  public GenericContentEntityOrigin getOrigin() {
    return new GenericContentEntityOriginImpl(entityReference, roots);
  }
}
