// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.InstrumentationClassFinder;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public class AbiJarBuilder extends ZipOutputBuilderImpl {

  @Nullable
  private final InstrumentationClassFinder myClassFinder;

  public AbiJarBuilder(Map<String, byte[]> dataSwap, Path readZipPath, Path writeZipPath, @Nullable InstrumentationClassFinder classFinder) throws IOException {
    super(dataSwap, readZipPath, writeZipPath);
    myClassFinder = classFinder;
  }

  @Override
  public void putEntry(String entryName, byte[] content) {
    byte[] filtered = filterAbiJarContent(entryName, content);
    if (filtered != null) {
      super.putEntry(entryName, filtered);
    }
  }

  private byte @Nullable [] filterAbiJarContent(String entryName, byte[] content) {
    if (myClassFinder == null || content == null || !entryName.endsWith(".class")) {
      if (entryName.endsWith(".java")) {
        return null;  // do not save AP-produced sources in abi jar
      }
      return content; // no instrumentation, if the entry is not a class file, or the class finder is not specified
    }
    // todo: for kotlin-generated classes use KotlinAnnotationVisitor, abiMetadataProcessor
    return JavaAbiClassFilter.filter(content, myClassFinder); // also strips debug-info and code data
  }

}
