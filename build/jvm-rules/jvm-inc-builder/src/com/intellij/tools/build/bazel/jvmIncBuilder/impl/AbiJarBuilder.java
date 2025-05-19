// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import com.dynatrace.hash4j.hashing.HashStream64;
import com.dynatrace.hash4j.hashing.Hashing;
import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

import static com.intellij.tools.build.bazel.jvmIncBuilder.ZipOutputBuilder.getParentEntryName;
import static com.intellij.tools.build.bazel.jvmIncBuilder.ZipOutputBuilder.isDirectoryName;
import static org.jetbrains.jps.util.Iterators.collect;
import static org.jetbrains.jps.util.Iterators.filter;

public class AbiJarBuilder extends ZipOutputBuilderImpl {
  private static final String PACKAGE_INDEX_STORAGE_ENTRY_NAME = "__package_index__";

  private final Map<String, Long> myPackageIndex = new TreeMap<>(); // directoryEntryName -> digestOf(content entries)
  private boolean myPackageIndexChanged;
  private boolean myShouldStoreIndex;
  @Nullable
  private final InstrumentationClassFinder myClassFinder;

  public AbiJarBuilder(Path zipPath) throws IOException {
    this(zipPath, zipPath);
  }

  public AbiJarBuilder(Path readZipPath, Path writeZipPath) throws IOException {
    this(readZipPath, writeZipPath, null);
  }

  public AbiJarBuilder(Path readZipPath, Path writeZipPath, @Nullable InstrumentationClassFinder classFinder) throws IOException {
    super(readZipPath, writeZipPath);
    myClassFinder = classFinder;
    byte[] content = getContent(PACKAGE_INDEX_STORAGE_ENTRY_NAME);
    if (content != null) {
      readPackageIndex(content, myPackageIndex);
    }
    else {
      // force package index generation on close 
      myPackageIndexChanged = true;
    }
  }

  private static void readPackageIndex(byte[] content, Map<String, Long> index) {
    if (content != null) {
      try {
        try (var is = new DataInputStream(new ByteArrayInputStream(content))) {
          int size = is.readInt();
          while (size-- > 0) {
            long digest = is.readLong();
            String entryName = is.readUTF();
            index.put(entryName, digest);
          }
        }
      }
      catch (IOException ignored) {
      }
    }
  }

  private static byte[] savePackageIndex(Map<String, Long> index) {
    if (index.isEmpty()) {
      return new byte[0];
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      try (var os = new DataOutputStream(out)) {
        os.writeInt(index.size());
        for (Map.Entry<String, Long> entry : index.entrySet()) {
          os.writeLong(entry.getValue());
          os.writeUTF(entry.getKey());
        }
      }
    }
    catch (IOException ignored) {
    }
    return out.toByteArray();
  }

  /**
   * @return An unmodifiable map of [packageName -> digest] where digest reflects the state of all classes currently present in the package
   */
  public Map<String, Long> getPackageIndex() {
    updatePackageIndex();
    return Collections.unmodifiableMap(myPackageIndex);
  }

  @Override
  public void putEntry(String entryName, byte[] content) {
    byte[] filtered = filterAbiJarContent(entryName, content);
    if (filtered != null) {
      super.putEntry(entryName, filtered);
      myPackageIndexChanged |= myPackageIndex.remove(getParentEntryName(entryName)) != null;
    }
    else {
      deleteEntry(entryName);
    }
  }

  private byte @Nullable [] filterAbiJarContent(String entryName, byte[] content) {
    if (myClassFinder == null) {
      return content; // no instrumentation, if class finder is not specified
    }

    if (entryName.endsWith(".kotlin_module")) {
      return content; // don't apply filtering on kotlin module, todo: check if we need this in abi jar
    }
    // todo: check content and instrument it before adding
    // todo: for java use JavaAbiClassVisitor, for kotlin-generated classes use KotlinAnnotationVisitor, abiMetadataProcessor
    return JavaAbiClassFilter.filter(content, myClassFinder);
  }

  @Override
  public boolean deleteEntry(String entryName) {
    if (super.deleteEntry(entryName)) {
      if (isDirectoryName(entryName)) {
        myPackageIndexChanged = true; // force recalculation
        myPackageIndex.remove(entryName);
      }
      else {
        myPackageIndexChanged |= myPackageIndex.remove(getParentEntryName(entryName)) != null;
      }
      return true;
    }
    return false;
  }

  @Override
  public void close(boolean saveChanges) throws IOException {
    if (saveChanges) {
      if (myPackageIndexChanged || myShouldStoreIndex) { 
        updatePackageIndex();
        super.putEntry(PACKAGE_INDEX_STORAGE_ENTRY_NAME, savePackageIndex(myPackageIndex));
      }
    }
    super.close(saveChanges);
  }

  private void updatePackageIndex() {
    if (myPackageIndexChanged) {
      myShouldStoreIndex = true;
      Set<String> allDirEntries = new LinkedHashSet<>();
      for (String entryName : getEntryNames()) {
        allDirEntries.add(getParentEntryName(entryName));
      }
      myPackageIndex.keySet().retainAll(allDirEntries);
      for (String dirEntry : allDirEntries) {
        myPackageIndex.computeIfAbsent(dirEntry, this::calculateDigest);
      }
      myPackageIndexChanged = false;
    }
  }

  private long calculateDigest(String entryName) {
    List<String> nodeNames = collect(filter(listEntries(entryName), n -> !isDirectoryName(n)), new ArrayList<>());
    Collections.sort(nodeNames);
    HashStream64 stream = Hashing.xxh3_64().hashStream();
    for (String nodeName : nodeNames) {
      stream.putString(nodeName);
      byte[] data = getContent(nodeName);
      if (data != null) {
        stream.putByteArray(data);
      }
    }
    return stream.getAsLong();
  }

}
