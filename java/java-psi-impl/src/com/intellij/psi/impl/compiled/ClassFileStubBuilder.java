// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.compiled.ClassFileDecompilers;
import com.intellij.psi.stubs.BinaryFileStubBuilder;
import com.intellij.psi.stubs.Stub;
import com.intellij.util.cls.ClsFormatException;
import com.intellij.util.indexing.FileContent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

import static com.intellij.psi.compiled.ClassFileDecompilers.Full;

public class ClassFileStubBuilder implements BinaryFileStubBuilder.CompositeBinaryFileStubBuilder<Full> {
  private static final Logger LOG = Logger.getInstance(ClassFileStubBuilder.class);

  public static final int STUB_VERSION = 26;

  @Override
  public boolean acceptsFile(@NotNull VirtualFile file) {
    return true;
  }

  @Override
  public @NotNull Stream<Full> getAllSubBuilders() {
    // currently, `getExtensionList` is a more safe than `extensions` because incompatible extension is filtered out (if extension class loaded from different classloader)
    // even more - getAllSubBuilders is called quite often, no need to use iterator here
    return ClassFileDecompilers.getInstance().EP_NAME.getExtensionList().stream().filter(d -> d instanceof Full).map(d -> (Full)d);
  }

  @Override
  public @Nullable Full getSubBuilder(@NotNull FileContent fileContent) {
    return fileContent.getFile()
      .computeWithPreloadedContentHint(fileContent.getContent(), () -> ClassFileDecompilers.getInstance().find(fileContent.getFile(), Full.class));
  }

  @Override
  public @NotNull String getSubBuilderVersion(@Nullable Full decompiler) {
    if (decompiler == null) return "default";
    int version = decompiler.getStubBuilder().getStubVersion();
    return decompiler.getClass().getName() + ":" + version;
  }

  @Override
  public @Nullable Stub buildStubTree(@NotNull FileContent fileContent, @Nullable Full decompiler) {
    if (decompiler == null) return null;
    return fileContent.getFile().computeWithPreloadedContentHint(fileContent.getContent(), () -> {
      VirtualFile file = fileContent.getFile();
      try {
        return decompiler.getStubBuilder().buildFileStub(fileContent);
      }
      catch (ClsFormatException e) {
        if (LOG.isDebugEnabled()) LOG.debug(file.getPath(), e);
        else LOG.info(file.getPath() + ": " + e.getMessage());
      }
      return null;
    });
  }

  @Override
  public int getStubVersion() {
    return STUB_VERSION;
  }
}