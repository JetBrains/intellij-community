// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import com.intellij.tools.build.bazel.jvmIncBuilder.ZipOutputBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class JavaAbiFilter implements ZipOutputBuilder {
  private @NotNull final ZipOutputBuilder myDelegate;

  public JavaAbiFilter(@NotNull ZipOutputBuilder delegate) {
    myDelegate = delegate;
  }

  @Override
  public Iterable<String> getEntryNames() {
    return myDelegate.getEntryNames();
  }

  @Override
  public Iterable<String> listEntries(String entryName) {
    return myDelegate.listEntries(entryName);
  }

  @Override
  public byte @Nullable [] getContent(String entryName) {
    return myDelegate.getContent(entryName);
  }

  @Override
  public void putEntry(String entryName, byte[] content) {
    byte[] filtered = filterAbiJarContent(entryName, content);
    if (filtered != null) {
      myDelegate.putEntry(entryName, filtered);
    }
  }

  @Override
  public boolean deleteEntry(String entryName) {
    return myDelegate.deleteEntry(entryName);
  }

  @Override
  public void close() throws IOException {
    myDelegate.close();
  }

  @Override
  public void close(boolean saveChanges) throws IOException {
    myDelegate.close(saveChanges);
  }

  private static byte @Nullable [] filterAbiJarContent(String entryName, byte[] content) {
    if (content == null || !entryName.endsWith(".class")) {
      if (entryName.endsWith(".java")) {
        // do not save annotation processors (AP)-produced sources in abi jar
        // AP generate sources that we must store somewhere during the compilation to give them back to javac, if it needs them.
        // Eventually, those sources are deleted from the output. With this check the JavaAbiFilter filter ensures that no sources get into the ABI output.
        return null;
      }
      return content; // no instrumentation, if the entry is not a class file, or the class finder is not specified
    }
    return JavaAbiClassFilter.filter(content); // also strips debug-info and code data
  }

}
