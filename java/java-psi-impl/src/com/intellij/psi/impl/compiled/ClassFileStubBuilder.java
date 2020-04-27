// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.compiled.ClassFileDecompilers;
import com.intellij.psi.stubs.BinaryFileStubBuilder;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.stubs.Stub;
import com.intellij.util.cls.ClsFormatException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.InvertedIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.intellij.psi.compiled.ClassFileDecompilers.Full;

public class ClassFileStubBuilder implements BinaryFileStubBuilder.CompositeBinaryFileStubBuilder<ClassFileDecompilers.Decompiler> {
  private static final Logger LOG = Logger.getInstance(ClassFileStubBuilder.class);

  public static final int STUB_VERSION = 23;

  @Override
  public boolean acceptsFile(@NotNull VirtualFile file) {
    return true;
  }

  @NotNull
  @Override
  public Stream<ClassFileDecompilers.Decompiler> getAllSubBuilders() {
    return ClassFileDecompilers.EP_NAME.extensions();
  }

  @Nullable
  @Override
  public ClassFileDecompilers.Decompiler getSubBuilder(@NotNull FileContent fileContent) {
    return setContentAndCompute(fileContent, () -> ClassFileDecompilers.find(fileContent.getFile()));
  }

  @NotNull
  @Override
  public String getSubBuilderVersion(@Nullable ClassFileDecompilers.Decompiler decompiler) {
    if (decompiler == null) return "default";
    int version = decompiler instanceof Full ? ((Full)decompiler).getStubBuilder().getStubVersion() : 0;
    return decompiler.getClass().getName() + ":" + version;
  }

  @Nullable
  @Override
  public Stub buildStubTree(@NotNull FileContent fileContent, @Nullable ClassFileDecompilers.Decompiler decompiler) {
    return setContentAndCompute(fileContent, () -> {
      VirtualFile file = fileContent.getFile();
      try {
        if (decompiler instanceof Full) {
          return ((Full) decompiler).getStubBuilder().buildFileStub(fileContent);
        }
      } catch (ClsFormatException e) {
        if (LOG.isDebugEnabled()) LOG.debug(file.getPath(), e);
        else LOG.info(file.getPath() + ": " + e.getMessage());
      }

      try {
        PsiFileStub<?> stub = ClsFileImpl.buildFileStub(file, fileContent.getContent());
        if (stub == null && fileContent.getFileName().indexOf('$') < 0) {
          LOG.info("No stub built for the file " + fileContent);
        }
        return stub;
      } catch (ClsFormatException e) {
        if (LOG.isDebugEnabled()) LOG.debug(file.getPath(), e);
        else LOG.info(file.getPath() + ": " + e.getMessage());
      }

      return null;
    });
  }

  private static final Comparator<Object> CLASS_NAME_COMPARATOR = Comparator.comparing(o -> o.getClass().getName());

  @Override
  public int getStubVersion() {
    int version = STUB_VERSION;

    if (!InvertedIndex.ARE_COMPOSITE_INDEXERS_ENABLED) {
      List<ClassFileDecompilers.Decompiler> decompilers = ContainerUtil.newArrayList(ClassFileDecompilers.EP_NAME.getExtensions());
      decompilers.sort(CLASS_NAME_COMPARATOR);
      for (ClassFileDecompilers.Decompiler decompiler : decompilers) {
        if (decompiler instanceof Full) {
          version = version * 31 + ((Full)decompiler).getStubBuilder().getStubVersion() + decompiler.getClass().getName().hashCode();
        }
      }
    }

    return version;
  }

  private static <T> T setContentAndCompute(@NotNull FileContent content, @NotNull Supplier<T> computation) {
    try {
      content.getFile().setPreloadedContentHint(content.getContent());
      return computation.get();
    } finally {
      content.getFile().setPreloadedContentHint(null);
    }
  }
}