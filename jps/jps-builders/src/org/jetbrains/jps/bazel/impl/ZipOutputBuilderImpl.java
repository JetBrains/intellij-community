// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.bazel.ZipOutputBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.jetbrains.jps.javac.Iterators.*;

public class ZipOutputBuilderImpl implements ZipOutputBuilder {
  private static final byte[] EMPTY_BYTES = new byte[0];
  
  private final Map<String, EntryData> myEntries = new TreeMap<>();
  private final Map<String, ZipEntry> myDirectoryEntries = new HashMap<>();
  private final ZipFile myZipFile;
  @NotNull
  private final Path myOutputZip;
  private boolean myHasChanges;

  public ZipOutputBuilderImpl(Path outputZip) throws IOException {
    myZipFile = new ZipFile(outputZip.toFile());
    myOutputZip = outputZip;
    Enumeration<? extends ZipEntry> entries = myZipFile.entries();
    while (entries.hasMoreElements()) {
      ZipEntry entry = entries.nextElement();
      if (entry.isDirectory()) {
        myDirectoryEntries.put(entry.getName(), entry);
      }
      else {
        myEntries.put(entry.getName(), EntryData.create(myZipFile, entry));
      }
    }
  }

  @Override
  public Iterable<String> getEntryNames() {
    return myEntries.keySet();
  }

  @Override
  public boolean isDirectory(String entryName) {
    return isDirectoryName(entryName);
  }

  @Override
  public byte[] getContent(String entryName) {
    try {
      return myEntries.getOrDefault(entryName, EntryData.DIR_DATA).getContent();
    }
    catch (IOException e) {
      // todo: diagnostics
      return EMPTY_BYTES;
    }
  }

  @Override
  public void putEntry(String entryName, byte[] content) {
    if (isDirectoryName(entryName)) {
      throw new RuntimeException("Unexpected name with trailing slash for ZIP entry with content: \"" + entryName + "\"");
    }
    myEntries.put(entryName, EntryData.create(entryName, content));
    myHasChanges = true;
  }

  @Override
  public void deleteEntry(String entryName) {
    if (myEntries.remove(entryName) != null) {
      myHasChanges = true;
    }
  }

  @Override
  public void close(boolean saveChanges) throws IOException {
    if (!myHasChanges || !saveChanges) {
      myZipFile.close();
    }
    else {
      // augment entry map with all currently present directory entries
      for (String dirName : collect(flat(map(myEntries.keySet(), ZipOutputBuilderImpl::allParentNames)), new HashSet<>())) {
        ZipEntry existingEntry = myDirectoryEntries.get(dirName);
        myEntries.put(dirName, EntryData.create(myZipFile, existingEntry != null? existingEntry : new ZipEntry(dirName)));
      }
      Path newOutputName = getNewOutputName();
      try (var zos = new ZipOutputStream(Files.newOutputStream(newOutputName))) {
        for (Iterator<Map.Entry<String, EntryData>> it = myEntries.entrySet().iterator(); it.hasNext(); ) {
          EntryData data = it.next().getValue();
          ZipEntry zipEntry = data.getZipEntry();
          zos.putNextEntry(zipEntry);
          if (!zipEntry.isDirectory()) {
            // either new content or the one loaded from the previous file
            zos.write(data.getContent());
          }
          it.remove();
        }
      }
      finally {
        myZipFile.close();
        Files.move(newOutputName, myOutputZip, StandardCopyOption.REPLACE_EXISTING);
      }
    }
  }

  private @NotNull Path getNewOutputName() {
    // todo: handle situation when file exists
    return myOutputZip.resolveSibling(myOutputZip.getFileName() + ".tmp");
  }

  @Nullable
  private static String getParent(String entryName) {
    int idx = isDirectoryName(entryName)? entryName.lastIndexOf('/', entryName.length() - 2) : entryName.lastIndexOf('/');
    return idx >= 0? entryName.substring(0, idx + 1) : null;
  }

  private static boolean isDirectoryName(String entryName) {
    return entryName.endsWith("/");
  }

  private interface EntryData {
    EntryData DIR_DATA = new EntryData() {
      @Override
      public byte[] getContent() {
        return EMPTY_BYTES;
      }

      @Override
      public ZipEntry getZipEntry() {
        return null;
      }
    };

    byte[] getContent() throws IOException;

    ZipEntry getZipEntry();

    static EntryData create(String entryName, byte[] content) {
      return new EntryData() {
        private ZipEntry entry;
        @Override
        public byte[] getContent() {
          return content;
        }

        @Override
        public ZipEntry getZipEntry() {
          return entry != null? entry : (entry = new ZipEntry(entryName));
        }
      };
    }

    static EntryData create(ZipFile zip, ZipEntry entry) {
      if (entry.isDirectory()) {
        return new EntryData() {
          @Override
          public byte[] getContent() {
            return EMPTY_BYTES;
          }

          @Override
          public ZipEntry getZipEntry() {
            return entry;
          }
        };
      }
      return new EntryData() {
        private byte[] loaded;
        @Override
        public byte[] getContent() throws IOException {
          if (loaded == null) {
            try (InputStream is = zip.getInputStream(entry)) {
              loaded = is.readAllBytes();
            }
          }
          return loaded;
        }

        @Override
        public ZipEntry getZipEntry() {
          return entry;
        }
      };
    }
  }

  private static Iterable<String> allParentNames(String entryName) {
    String parent = getParent(entryName);
    return parent == null? List.of() : () -> new Iterator<>() {
      private String next = parent;
      @Override
      public boolean hasNext() {
        return next != null;
      }

      @Override
      public String next() {
        if (next == null) {
          throw new NoSuchElementException();
        }
        String result = next;
        next = getParent(next);
        return result;
      }
    };
  }

}
