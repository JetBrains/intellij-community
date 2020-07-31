// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.FileContent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

/**
 * @author max
 */
public interface BinaryFileStubBuilder {
  boolean acceptsFile(@NotNull VirtualFile file);

  @Nullable Stub buildStubTree(@NotNull FileContent fileContent);

  int getStubVersion();

  interface CompositeBinaryFileStubBuilder<SubBuilder> extends BinaryFileStubBuilder {
    @NotNull Stream<SubBuilder> getAllSubBuilders();

    @Nullable SubBuilder getSubBuilder(@NotNull FileContent fileContent);

    @NotNull String getSubBuilderVersion(@Nullable SubBuilder subBuilder);

    @Nullable Stub buildStubTree(@NotNull FileContent fileContent, @Nullable SubBuilder builder);

    @Override
    default @Nullable Stub buildStubTree(@NotNull FileContent fileContent) {
      return buildStubTree(fileContent, getSubBuilder(fileContent));
    }
  }
}