// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.platform.workspace.storage.EntityPointer;
import com.intellij.util.indexing.IndexingBundle;
import com.intellij.util.indexing.roots.origin.GenericContentEntityOrigin;
import com.intellij.util.indexing.roots.origin.GenericContentEntityOriginImpl;
import com.intellij.util.indexing.roots.origin.IndexingRootHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public final class GenericContentEntityIteratorImpl extends RootHolderIteratorBase {

  public GenericContentEntityIteratorImpl(@NotNull EntityPointer<?> entityPointer,
                                          @NotNull IndexingRootHolder roots,
                                          @Nullable IndexableIteratorPresentation presentation) {
    super(entityPointer, roots, presentation != null
                                  ? presentation
                                  : IndexableIteratorPresentation.create(
                                    "Module unaware content roots from entity (" + roots.getDebugDescription() + ")",
                                    IndexingBundle.message("indexable.files.provider.indexing.content"),
                                    IndexingBundle.message("indexable.files.provider.scanning.content")));
  }

  @Override
  public @NotNull GenericContentEntityOrigin getOrigin() {
    return new GenericContentEntityOriginImpl(myEntityPointer, roots);
  }
}
