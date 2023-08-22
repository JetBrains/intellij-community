// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.platform.workspace.storage.EntityReference;
import com.intellij.util.indexing.IndexingBundle;
import com.intellij.util.indexing.roots.origin.ExternalEntityOrigin;
import com.intellij.util.indexing.roots.origin.ExternalEntityOriginImpl;
import com.intellij.util.indexing.roots.origin.IndexingSourceRootHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ExternalEntityIndexableIteratorImpl extends SourceRootHolderIteratorBase {

  public ExternalEntityIndexableIteratorImpl(@NotNull EntityReference<?> entityReference,
                                             @NotNull IndexingSourceRootHolder roots,
                                             @Nullable IndexableIteratorPresentation presentation) {
    super(entityReference, roots, presentation != null
                                  ? presentation
                                  : IndexableIteratorPresentation.create("External roots from entity (" + roots.getRootsDebugStr() + ")",
                                                                         IndexingBundle.message(
                                                                           "indexable.files.provider.indexing.additional.dependencies"),
                                                                         IndexingBundle.message(
                                                                           "indexable.files.provider.scanning.additional.dependencies")));
  }

  @NotNull
  @Override
  public ExternalEntityOrigin getOrigin() {
    return new ExternalEntityOriginImpl(entityReference, roots);
  }
}
