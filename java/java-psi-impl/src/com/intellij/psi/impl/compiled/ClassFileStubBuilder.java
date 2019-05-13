/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.compiled.ClassFileDecompilers;
import com.intellij.psi.stubs.BinaryFileStubBuilder;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.cls.ClsFormatException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileContent;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;

import static com.intellij.psi.compiled.ClassFileDecompilers.Full;

/**
 * @author max
 */
public class ClassFileStubBuilder implements BinaryFileStubBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClassFileStubBuilder");

  public static final int STUB_VERSION = 22;

  @Override
  public boolean acceptsFile(@NotNull VirtualFile file) {
    return true;
  }

  @Override
  public StubElement buildStubTree(@NotNull FileContent fileContent) {
    VirtualFile file = fileContent.getFile();
    byte[] content = fileContent.getContent();

    try {
      try {
        file.setPreloadedContentHint(content);
        ClassFileDecompilers.Decompiler decompiler = ClassFileDecompilers.find(file);
        if (decompiler instanceof Full) {
          return ((Full)decompiler).getStubBuilder().buildFileStub(fileContent);
        }
      }
      catch (ClsFormatException e) {
        if (LOG.isDebugEnabled()) LOG.debug(file.getPath(), e);
        else LOG.info(file.getPath() + ": " + e.getMessage());
      }

      try {
        PsiFileStub<?> stub = ClsFileImpl.buildFileStub(file, content);
        if (stub == null && fileContent.getFileName().indexOf('$') < 0) {
          LOG.info("No stub built for file " + fileContent);
        }
        return stub;
      }
      catch (ClsFormatException e) {
        if (LOG.isDebugEnabled()) LOG.debug(file.getPath(), e);
        else LOG.info(file.getPath() + ": " + e.getMessage());
      }
    }
    finally {
      file.setPreloadedContentHint(null);
    }

    return null;
  }

  private static final Comparator<Object> CLASS_NAME_COMPARATOR = Comparator.comparing(o -> o.getClass().getName());

  @Override
  public int getStubVersion() {
    int version = STUB_VERSION;

    List<ClassFileDecompilers.Decompiler> decompilers = ContainerUtil.newArrayList(ClassFileDecompilers.EP_NAME.getExtensions());
    decompilers.sort(CLASS_NAME_COMPARATOR);
    for (ClassFileDecompilers.Decompiler decompiler : decompilers) {
      if (decompiler instanceof Full) {
        version = version * 31 + ((Full)decompiler).getStubBuilder().getStubVersion() + decompiler.getClass().getName().hashCode();
      }
    }

    return version;
  }
}