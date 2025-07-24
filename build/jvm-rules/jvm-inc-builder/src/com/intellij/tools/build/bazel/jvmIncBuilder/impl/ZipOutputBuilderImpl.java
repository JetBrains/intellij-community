// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import com.intellij.tools.build.bazel.jvmIncBuilder.ZipOutputBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.impl.RW;

import java.io.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.ref.SoftReference;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static com.intellij.tools.build.bazel.jvmIncBuilder.ZipOutputBuilder.*;
import static org.jetbrains.jps.util.Iterators.*;

public class ZipOutputBuilderImpl implements ZipOutputBuilder {
  private final Map<String, EntryData> myEntries = new TreeMap<>();
  private final Map<String, ZipEntry> myExistingDirectories = new HashMap<>();

  private final @NotNull Path myWriteZipPath;
  private final @NotNull Path myReadZipPath;
  private final @Nullable ZipFile myReadZipFile;

  private final Map<String, byte[]> mySwap;
  private final Map<String, Set<String>> myDirIndex = new HashMap<>();
  private boolean myHasChanges;

  public ZipOutputBuilderImpl(Path zipPath) throws IOException {
    this(zipPath, zipPath);
  }
  
  public ZipOutputBuilderImpl(@NotNull Path readZipPath, @NotNull Path writeZipPath) throws IOException {
    this(new HashMap<>(), readZipPath, writeZipPath);
  }
  
  public ZipOutputBuilderImpl(Map<String, byte[]> dataSwap, @NotNull Path readZipPath, @NotNull Path writeZipPath) throws IOException {
    myReadZipPath = readZipPath;
    myWriteZipPath = writeZipPath;
    mySwap = dataSwap;
    myReadZipFile = openZipFile(readZipPath);
    if (myReadZipFile != null) {
      // load existing entries
      Enumeration<? extends ZipEntry> entries = myReadZipFile.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        addToPackageIndex(entry.getName());
        if (entry.isDirectory()) {
          myExistingDirectories.put(entry.getName(), entry);
        }
        else {
          myEntries.put(entry.getName(), EntryData.create(myReadZipFile, entry));
        }
      }
    }
  }

  public boolean isInputZipExist() {
    return myReadZipFile != null;
  }
  
  private static @Nullable ZipFile openZipFile(Path zipPath) throws IOException {
    try {
      return new ZipFile(zipPath.toFile());
    }
    catch (NoSuchFileException ignored) {
    }
    return null;
  }

  @Override
  public Iterable<String> getEntryNames() {
    return myEntries.keySet();
  }

  @Override
  public Iterable<String> listEntries(String entryName) {
    if (!isDirectoryName(entryName)) {
      return Set.of();
    }
    Set<String> children = myDirIndex.getOrDefault(entryName, Set.of());
    return "/".equals(entryName)? children : map(children, n -> entryName + n);
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
    if (content != null) {
      myEntries.put(entryName, EntryData.create(mySwap, entryName, content));
      addToPackageIndex(entryName);
      myHasChanges = true;
    }
    else {
      myHasChanges |= deleteEntry(entryName);
    }
  }

  @Override
  public boolean deleteEntry(String entryName) {
    boolean changes = false;
    EntryData data = myEntries.remove(entryName);
    if (data != null) { 
      data.cleanup();
      changes = true;
    }
    changes |= removeFromPackageIndex(entryName);
    if (changes) {
      myHasChanges = true;
    }
    return changes;
  }

  @Override
  public void close() throws IOException {
    close(false);
  }

  @Override
  public void close(boolean saveChanges) throws IOException {
    try {
      if (!myHasChanges || !saveChanges) {
        if (myReadZipFile != null) {
          myReadZipFile.close();
          if (saveChanges && !myReadZipPath.equals(myWriteZipPath)) {
            // ensure content at the destination path is the same as the source content
            if (!Files.exists(myWriteZipPath) || !Files.isSameFile(myReadZipPath, myWriteZipPath)) {
              Files.copy(myReadZipPath, myWriteZipPath, StandardCopyOption.REPLACE_EXISTING);
            }
          }
        }
        else {
          if (saveChanges && !Files.exists(myWriteZipPath)) {
            // ensure an empty output file exists, even if there are no changes (bazel requirement)
            try (var zos = new ZipOutputStream(openOutputStream(myWriteZipPath))) {
              zos.setLevel(Deflater.BEST_SPEED);
            }
          }
        }
      }
      else {
        boolean useTempOutput = myReadZipFile != null /*srcZip exists*/ && Files.exists(myWriteZipPath) && Files.isSameFile(myReadZipPath, myWriteZipPath);
        Path outputPath = useTempOutput? getTempOutputPath() : myWriteZipPath;
        try (var zos = new ZipOutputStream(openOutputStream(outputPath))) {
          zos.setLevel(Deflater.BEST_SPEED);

          // augment entry map with all currently present directory entries
          for (String dirName : myDirIndex.keySet()) {
            ZipEntry existingEntry = myExistingDirectories.get(dirName);
            if (existingEntry == null && "/".equals(dirName)) {
              continue; // keep root '/' entry if it were present in the original zip
            }
            if (existingEntry != null) {
              myEntries.put(dirName, EntryData.create(myReadZipFile, existingEntry));
            }
            else {
              myEntries.put(dirName, EntryData.create(dirName, EntryData.NO_DATA_BYTES));
            }
          }

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
          if (myReadZipFile != null) {
            myReadZipFile.close();
          }
          if (useTempOutput) {
            Files.move(outputPath, myWriteZipPath, StandardCopyOption.REPLACE_EXISTING);
          }
        }
      }
    }
    finally {
      myExistingDirectories.clear();
      myDirIndex.clear();
      mySwap.clear();
    }
  }

  private static OutputStream openOutputStream(Path outputPath) throws IOException {
    try {
      return new BufferedOutputStream(Files.newOutputStream(outputPath));
    }
    catch (NoSuchFileException e) {
      Files.createDirectories(outputPath.getParent());
      return new BufferedOutputStream(Files.newOutputStream(outputPath));
    }
  }

  private @NotNull Path getTempOutputPath() {
    // todo: handle situation when file exists
    return myWriteZipPath.resolveSibling(myWriteZipPath.getFileName() + ".tmp");
  }

  private interface EntryData {
    byte[] NO_DATA_BYTES = new byte[0];
    
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
            return RW.readAllBytes(is);
          }
        }

        @Override
        public void transferTo(OutputStream os) throws IOException {
          byte[] data = getCached();
          if (data != null) {
            os.write(data);
          }
          else {
            RW.transferTo(StreamAccessor.unwrapInputStream(zip.getInputStream(entry)), StreamAccessor.unwrapOutputStream(os));
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
    private SoftReference<byte[]> myCached;

    CachingDataEntry(byte[] data) {
      cacheData(data);
    }

    protected abstract byte[] loadData() throws IOException;

    private byte[] cacheData(byte[] data) {
      myCached = data != null? new SoftReference<>(data) : null;
      return data;
    }

    @Override
    public final byte[] getContent() throws IOException {
      byte[] data = getCached();
      return data != null? data : cacheData(loadData());
    }

    protected final byte @Nullable [] getCached() {
      SoftReference<byte[]> cached = myCached;
      return cached != null? cached.get() : null;
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

  private Iterable<String> allParentNames(String entryName) {
    String parent = getParentEntryName(entryName);
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
        next = getParentEntryName(next);
        return result;
      }
    };
  }

  private void addToPackageIndex(String entryName) {
    String parent = getParentEntryName(entryName);
    if (parent != null) {
      boolean added = myDirIndex.computeIfAbsent(parent, k -> new HashSet<>()).add(getShortName(entryName));
      if (added) {
        addToPackageIndex(parent);
      }
    }
  }

  private boolean removeFromPackageIndex(String entryName) {
    boolean changes = false;
    if (isDirectoryName(entryName)) {
      Set<String> toRemove = collect(recurseDepth(entryName, this::listEntries, true), new HashSet<>());
      changes |= myDirIndex.keySet().removeAll(toRemove);
      for (String name : filter(toRemove, n -> !isDirectoryName(n))) {
        EntryData data = myEntries.remove(name);
        if (data != null) {
          data.cleanup();
          changes = true;
        }
      }
    }
    for (String parent = getParentEntryName(entryName); parent != null; parent = getParentEntryName(entryName)) {
      Set<String> children = myDirIndex.get(parent);
      if (children == null) {
        break; // not associated
      }
      changes |= children.remove(getShortName(entryName));
      if (children.isEmpty()) {
        myDirIndex.remove(parent);
        entryName = parent;
      }
      else {
        break;
      }
    }
    return changes;
  }
}
