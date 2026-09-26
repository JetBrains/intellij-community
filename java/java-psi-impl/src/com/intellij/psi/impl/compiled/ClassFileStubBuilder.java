// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
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

  public static final int STUB_VERSION = 32;

  @Override
  public @NotNull VirtualFileFilter getFileFilter() {
    return VirtualFileFilter.ALL; // any file of file type that this builder is registered for
  }

  @Override
  public boolean acceptsFile(@NotNull VirtualFile file) {
    return true;
  }

  @Override
  public @NotNull Stream<Full> getAllSubBuilders() {
    return ClassFileDecompilers.STATIC_EP_NAME.getExtensionList().stream().filter(d -> d instanceof Full).map(d -> (Full)d);
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
        //TODO: decompiler may use not only the fileContent provided, but a files around: e.g. inner-class files, see ClsFileImpl.findInnerClass()
        //      method.
        //      This is a violation of indexing contract: indexing data for a file X should depend _only_ on X.content, NOT on
        //      any other files state/existence/content. Violation of this contract leads to indexes being outdated/inconsistent,
        //      because indexing subsystem is not able to reliably detect when and which files needs to be re-indexed.
        //      It is still possible to use it here, because we rely on Compiler to always modify inner and outer class-files
        //      _together_. If the modification of inner-class are _always_ accompanied by the modification of outer (host) class
        //      => outer-class modification will be detected by indexing subsystem as a reason to re-index the outer-class and,
        //      consequently, the inter class, too.
        //      The scheme is fragile, lawless, and probably pleases the Satan -- but so far it works.
        //      Don't use something like that yourself, though: it is a hack, and such hacks could be meaningfully employed only by
        //      ruthless and highly skilled Dark Jedi. Don't play with the Dark Side -- follow the indexing contract strictly.
        return decompiler.getStubBuilder().buildFileStub(fileContent);
      }
      catch (ClsFormatException e) {
        if (LOG.isDebugEnabled()) {
          LOG.debug(file.getPath(), e);
        }
        else {
          LOG.info(file.getPath() + ": " + e.getMessage());
        }
      }
      return null;
    });
  }

  @Override
  public int getStubVersion() {
    return STUB_VERSION;
  }
}