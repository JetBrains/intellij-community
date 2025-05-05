// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel.impl;

import com.intellij.compiler.instrumentation.FailSafeClassReader;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.bazel.ZipOutputBuilder;
import org.jetbrains.org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import static org.jetbrains.jps.javac.Iterators.filter;
import static org.jetbrains.jps.javac.Iterators.map;

public interface ClassDataZipEntry {
  // zip entry name
  String getPath();

  @Nullable
  default String getParent() {
    return ZipOutputBuilder.getParentEntryName(getPath());
  }

  // asm class reader to read entry data
  ClassReader getClassReader();

  static boolean isClassDataEntry(String entryName) {
    return entryName.endsWith(".class");
  }

  static Iterator<ClassDataZipEntry> fromZipOutputBuilder(ZipOutputBuilder builder) {
    return map(filter(builder.getEntryNames().iterator(), ClassDataZipEntry::isClassDataEntry), entryName -> new ClassDataZipEntry() {
      private ClassReader reader = null;

      @Override
      public String getPath() {
        return entryName;
      }

      @Override
      public ClassReader getClassReader() {
        if (reader == null) {
          reader = new FailSafeClassReader(builder.getContent(entryName));
        }
        return reader;
      }
    });
  }

  static Iterator<ClassDataZipEntry> fromSteam(InputStream is) {
    return map(filter(new ZipEntryIterator(is), se -> isClassDataEntry(se.getEntry().getName())), se -> new ClassDataZipEntry() {
      private ClassReader reader = null;

      @Override
      public String getPath() {
        return se.getEntry().getName();
      }

      @Override
      public ClassReader getClassReader() {
        if (reader == null) {
          try {
            reader = new FailSafeClassReader(se.getStream());
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
        return reader;
      }
    });
  }
}
