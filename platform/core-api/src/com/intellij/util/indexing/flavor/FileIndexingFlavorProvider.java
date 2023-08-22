// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.flavor;

import com.intellij.openapi.fileTypes.FileTypeExtension;
import com.intellij.util.indexing.IndexedFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provider that allows to customize indexing hash evaluation {@link com.intellij.util.indexing.IndexedHashesSupport} via mixing additional objects to hash function.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
public interface FileIndexingFlavorProvider<Flavor> {
  FileTypeExtension<FileIndexingFlavorProvider<?>> INSTANCE = new FileTypeExtension<>("com.intellij.indexingFlavor");

  @Nullable
  Flavor getFlavor(@NotNull IndexedFile file);

  void buildHash(@NotNull Flavor flavor, @NotNull HashBuilder hashBuilder);

  int getVersion();

  @NotNull
  String getId();
}
