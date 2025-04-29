// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel.impl;

import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.OffHeapStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.bazel.ZipOutputBuilder;

import java.io.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.jetbrains.jps.javac.Iterators.*;

public class ZipOutputBuilderImpl implements ZipOutputBuilder {
  private final Map<String, EntryData> myEntries = new TreeMap<>();
  private final Map<String, ZipEntry> myDirectoryEntries = new HashMap<>();
  private final ZipFile myZipFile;
  @NotNull
  private final Path myOutputZip;
  private final MVStore myDataSwapStore;
  private final MVMap<String, byte[]> mySwap;
  private final Map<String, Set<String>> myPackageIndex = new HashMap<>();
  private boolean myHasChanges;

  public ZipOutputBuilderImpl(Path outputZip) throws IOException {
    myOutputZip = outputZip;
    myDataSwapStore = new MVStore.Builder()
      .fileStore(new OffHeapStore())
      .autoCommitDisabled()
      .cacheSize(8)
      .open();
    myDataSwapStore.setVersionsToKeep(0);
    mySwap = myDataSwapStore.openMap("data-swap");
    myZipFile = new ZipFile(outputZip.toFile());
    Enumeration<? extends ZipEntry> entries = myZipFile.entries();
    while (entries.hasMoreElements()) {
      ZipEntry entry = entries.nextElement();
      addToPackageIndex(entry.getName());
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
  public Iterable<String> listEntries(String entryName) {
    return isDirectoryName(entryName)? myPackageIndex.getOrDefault(entryName, Set.of()) : List.of();
  }

  @Override
  public byte[] getContent(String entryName) {
    try {
      EntryData data = myEntries.get(entryName);
      return data != null? data.getContent() : null;
    }
    catch (IOException e) {
      // todo: diagnostics
      return null;
    }
  }

  @Override
  public void putEntry(String entryName, byte[] content) {
    if (isDirectoryName(entryName)) {
      throw new RuntimeException("Unexpected name with trailing slash for ZIP entry with content: \"" + entryName + "\"");
    }
    myEntries.put(entryName, EntryData.create(mySwap, entryName, content));
    addToPackageIndex(entryName);
    myHasChanges = true;
  }

  @Override
  public void deleteEntry(String entryName) {
    EntryData data = myEntries.remove(entryName);
    if (data != null) {
      data.cleanup();
      removeFromPackageIndex(entryName);
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
        ZipEntry existingEntry = myDirectoryEntries.get(dirName); // todo: need to generate '/' entry?
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
            data.transferTo(zos);
          }
          it.remove();
        }
      }
      finally {
        myZipFile.close();
        myDataSwapStore.close();
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
    if (entryName == null || entryName.isEmpty() || "/".equals(entryName)) {
      return null;
    }
    int idx = isDirectoryName(entryName)? entryName.lastIndexOf('/', entryName.length() - 2) : entryName.lastIndexOf('/');
    return idx > 0? entryName.substring(0, idx + 1) : "/";
  }

  private static boolean isDirectoryName(String entryName) {
    return entryName.endsWith("/");
  }

  private interface EntryData {
    byte[] NO_DATA_BYTES = new byte[0];
    
    EntryData DIR_DATA = new EntryData() {
      @Override
      public byte[] getContent() {
        return NO_DATA_BYTES;
      }

      @Override
      public ZipEntry getZipEntry() {
        return null;
      }
    };

    byte[] getContent() throws IOException;

    ZipEntry getZipEntry();

    default void transferTo(OutputStream os) throws IOException {
      byte[] data = getContent();
      if (data != NO_DATA_BYTES) {
        os.write(data);
      }
    }

    default void cleanup() {
    }
    
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

    static EntryData create(Map<String, byte[]> swap, String entryName, byte[] content) {
      swap.put(entryName, content);
      return new CachingDataEntry(content) {
        private ZipEntry entry;
        @Override
        protected byte[] loadData() {
          return swap.get(entryName);
        }

        @Override
        public ZipEntry getZipEntry() {
          return entry != null? entry : (entry = new ZipEntry(entryName));
        }

        @Override
        public void cleanup() {
          super.cleanup();
          entry = null;
          swap.remove(entryName);
        }
      };
    }

    static EntryData create(ZipFile zip, ZipEntry entry) {
      if (entry.isDirectory()) {
        return new EntryData() {
          @Override
          public byte[] getContent() {
            return NO_DATA_BYTES;
          }

          @Override
          public ZipEntry getZipEntry() {
            return entry;
          }
        };
      }
      return new CachingDataEntry(null) {
        @Override
        protected byte[] loadData() throws IOException {
          try (InputStream is = zip.getInputStream(entry)) {
            return is.readAllBytes();
          }
        }

        @Override
        public void transferTo(OutputStream os) throws IOException {
          byte[] data = getCached();
          if (data != null) {
            os.write(data);
          }
          else {
            StreamAccessor.unwrapInputStream(zip.getInputStream(entry)).transferTo(StreamAccessor.unwrapOutputStream(os));
          }
        }

        @Override
        public ZipEntry getZipEntry() {
          return entry;
        }
      };
    }

  }

  private static abstract class CachingDataEntry implements EntryData {
    private WeakReference<byte[]> myCached;

    CachingDataEntry(byte[] data) {
      cacheData(data);
    }

    protected abstract byte[] loadData() throws IOException;

    private byte[] cacheData(byte[] data) {
      myCached = data != null? new WeakReference<>(data) : null;
      return data;
    }

    @Override
    public final byte[] getContent() throws IOException {
      byte[] data = getCached();
      return data != null? data : cacheData(loadData());
    }

    protected final byte @Nullable [] getCached() {
      return myCached != null? myCached.get() : null;
    }

    @Override
    public void cleanup() {
      myCached = null;
    }
  }

  private static final class StreamAccessor {
    private static final MethodHandle outFieldAccessor;
    private static final MethodHandle inFieldAccessor;

    static {
      outFieldAccessor = getMethodHandle(FilterOutputStream.class, "out");
      inFieldAccessor = getMethodHandle(FilterInputStream.class, "in");
    }

    private static MethodHandle getMethodHandle(Class<?> aClass, String fieldName) {
      try {
        Field outField = aClass.getDeclaredField(fieldName);
        outField.setAccessible(true);
        return MethodHandles.lookup().unreflectGetter(outField);
      }
      catch (Throwable e) {
        return null;
      }
    }

    static InputStream unwrapInputStream(InputStream is) {
      //if (inFieldAccessor != null && is instanceof FilterInputStream) {
      //  try {
      //    return (InputStream) inFieldAccessor.invoke(is);
      //  }
      //  catch (Throwable ignored) {
      //  }
      //}
      return is;
    }

    static OutputStream unwrapOutputStream(OutputStream os) {
      //if (outFieldAccessor != null && os instanceof ZipOutputStream) {
      //  try {
      //    return (OutputStream) outFieldAccessor.invoke(os);
      //  }
      //  catch (Throwable ignored) {
      //  }
      //}
      return os;
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

  private void addToPackageIndex(String entryName) {
    String parent = getParent(entryName);
    if (parent != null) {
      boolean added = myPackageIndex.computeIfAbsent(parent, k -> new HashSet<>()).add(entryName);
      if (added) {
        addToPackageIndex(parent);
      }
    }
  }

  private void removeFromPackageIndex(String entryName) {
    String parent = getParent(entryName);
    if (parent != null) {
      Set<String> children = myPackageIndex.get(parent);
      if (children != null) {
        boolean removed = children.remove(entryName);
        if (removed && children.isEmpty()) {
          myPackageIndex.remove(parent);
          removeFromPackageIndex(parent);
        }
      }
    }
  }
}
