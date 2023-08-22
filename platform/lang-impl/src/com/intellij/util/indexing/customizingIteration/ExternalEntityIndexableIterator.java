// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.customizingIteration;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.util.indexing.IndexingBundle;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.util.indexing.roots.origin.ExternalEntityOrigin;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Will be removed as a part of {@link com.intellij.util.indexing.CustomizingIndexingContributor} API
 */
@Deprecated(forRemoval = true)
public interface ExternalEntityIndexableIterator extends IndexableFilesIterator {

  @NonNls
  @Override
  default String getDebugName() {
    return "External roots from entity";
  }

  @NlsContexts.ProgressText
  @Override
  default String getIndexingProgressText() {
    return IndexingBundle.message("indexable.files.provider.indexing.additional.dependencies");
  }

  @NlsContexts.ProgressText
  @Override
  default String getRootsScanningProgressText() {
    return IndexingBundle.message("indexable.files.provider.scanning.additional.dependencies");
  }

  @Override
  @NotNull
  ExternalEntityOrigin getOrigin();
}