// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import com.intellij.tools.build.bazel.jvmIncBuilder.ZipOutputBuilder;
import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.FailSafeClassReader;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.jetbrains.jps.util.Iterators.filter;
import static org.jetbrains.jps.util.Iterators.map;

public interface ClassDataZipEntry {
  // zip entry name
  String getPath();

  @Nullable
  default String getParent() {
    return ZipOutputBuilder.getParentEntryName(getPath());
  }

  byte[] getContent();

  // asm class reader to read entry data
  ClassReader getClassReader();

  static boolean isClassDataEntry(String entryName) {
    return entryName.endsWith(".class");
  }

  static Iterator<ClassDataZipEntry> fromZipOutputBuilder(ZipOutputBuilder builder) {
    return map(filter(builder.getEntryNames().iterator(), ClassDataZipEntry::isClassDataEntry), entryName -> create(entryName, builder));
  }

  static Iterator<ClassDataZipEntry> fromSteam(InputStream is) {
    return map(filter(new ZipEntryIterator(is), se -> isClassDataEntry(se.getEntry().getName())), se -> create(se.getEntry(), se.getStream()));
  }

  private static ClassDataZipEntry create(String entryName, ZipOutputBuilder zipData) {
    return new ClassDataZipEntry() {
      private ClassReader reader = null;

      @Override
      public String getPath() {
        return entryName;
      }

      @Override
      public byte[] getContent() {
        return zipData.getContent(entryName);
      }

      @Override
      public ClassReader getClassReader() {
        if (reader == null) {
          reader = new FailSafeClassReader(getContent());
        }
        return reader;
      }
    };
  }

  private static ClassDataZipEntry create(ZipEntry entry, ZipInputStream in) {
    try {
      byte[] bytes = in.readAllBytes();
      return new ClassDataZipEntry() {
        private final ClassReader reader = new FailSafeClassReader(bytes);

        @Override
        public String getPath() {
          return entry.getName();
        }

        @Override
        public byte[] getContent() {
          return bytes;
        }

        @Override
        public ClassReader getClassReader() {
          return reader;
        }
      };
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
