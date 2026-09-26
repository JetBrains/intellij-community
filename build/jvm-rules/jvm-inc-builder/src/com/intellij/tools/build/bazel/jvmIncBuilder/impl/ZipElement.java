package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public interface ZipElement {
  @NotNull
  ZipEntry getEntry();

  byte @NotNull [] getContent();

  static Iterable<ZipElement> fromZipFile(ZipFile zip) {
    return () -> {
      Enumeration<? extends ZipEntry> entries = zip.entries();
      return asIterator(() -> {
        if (!entries.hasMoreElements()) {
          return null;
        }
        ZipEntry entry = entries.nextElement();
        return new ZipElement() {
          private byte[] content = null;

          @Override
          public @NotNull ZipEntry getEntry() {
            return entry;
          }

          @Override
          public byte @NotNull [] getContent() {
            try {
              if (content == null) {
                try (InputStream is = zip.getInputStream(entry)) {
                  content = is.readAllBytes();
                }
              }
              return content;
            }
            catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
        };
      });
    };
  }

  static Iterator<ZipElement> fromZipStream(ZipInputStream zis) {
    return asIterator(() -> {
      try {
        ZipEntry entry = zis.getNextEntry();
        if (entry == null) {
          return null;
        }
        byte[] content = zis.readAllBytes();
        return new ZipElement() {
          @Override
          public @NotNull ZipEntry getEntry() {
            return entry;
          }

          @Override
          public byte @NotNull [] getContent() {
            return content;
          }
        };
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
  }

  private static <T> Iterator<T> asIterator(Supplier<T> nextSupplier) {
    return new Iterator<>() {
      private T nextEntry;

      @Override
      public boolean hasNext() {
        return getNextElement() != null;
      }

      @Override
      public T next() {
        try {
          return getNextElement();
        }
        finally {
          nextEntry = null;
        }
      }

      private T getNextElement() {
        if (nextEntry == null) {
          nextEntry = nextSupplier.get();
        }
        return nextEntry;
      }
    };
  }

}
