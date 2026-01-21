// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel;

import com.intellij.tools.build.bazel.jvmIncBuilder.ZipOutputBuilder;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.ZipOutputBuilderImpl;
import org.jetbrains.jps.util.Iterators;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.tools.build.bazel.jvmIncBuilder.ZipOutputBuilder.isDirectoryName;
import static org.jetbrains.jps.util.Iterators.collect;
import static org.jetbrains.jps.util.Iterators.isEmpty;
import static org.junit.Assert.*;

public class ZipBuilderTest {
  private Path myRootDir;

  @Before
  public void setUp() throws Exception {
    myRootDir = Files.createTempDirectory("zip-builder-test");
  }

  @After
  public void tearDown() throws Exception {
    Files.deleteIfExists(myRootDir);
  }

  @Test
  public void testZipCreation() throws IOException {
    Path zipFile = myRootDir.resolve("test-archive.zip");

    List<String> allEntriesSorted = List.of(
      "com/sys1/api/service1.class",
      "com/sys1/api/service2.class",
      "com/sys1/api/service3.class",
      "com/sys1/impl/service1Impl.class",
      "com/sys1/impl/service2Impl.class",
      "com/sys1/impl/service3Impl.class",

      "com/sys2/api/service1.class",
      "com/sys2/api/service2.class",
      "com/sys2/api/service3.class",
      "com/sys2/impl/service1Impl.class",
      "com/sys2/impl/service2Impl.class",
      "com/sys2/impl/service3Impl.class"
    );
    byte[] emptyContent = new byte[0];

    // creation
    try (ZipOutputBuilder builder = new ZipOutputBuilderImpl(zipFile)) {
      assertTrue(isEmpty(builder.listEntries("")));
      for (String entry : allEntriesSorted) {
        builder.putEntry(entry, emptyContent);
      }
      builder.close(true);
    }
    
    // reading
    try (ZipOutputBuilder builder = new ZipOutputBuilderImpl(zipFile)) {

      assertEquals(allEntriesSorted, collect(builder.getEntryNames(), new ArrayList<>()));

      Set<String> entries_com = collect(builder.listEntries("com/"), new HashSet<>());
      assertEquals(Set.of("com/sys1/", "com/sys2/"), entries_com);

      Set<String> entries_com_sys1 = collect(builder.listEntries("com/sys1/"), new HashSet<>());
      assertEquals(Set.of("com/sys1/api/", "com/sys1/impl/"), entries_com_sys1);

      Set<String> entries_com_sys1_api = collect(builder.listEntries("com/sys1/api/"), new HashSet<>());
      assertEquals(Set.of("com/sys1/api/service1.class", "com/sys1/api/service2.class", "com/sys1/api/service3.class"), entries_com_sys1_api);
    }
    

    // modification

    String descriptionContent = "system description";
    List<String> allEntriesAfterModificationSorted = List.of(
      "com/sys0/description.txt",
      "com/sys1/api/service1.class",
      "com/sys1/api/service2.class",
      "com/sys1/api/service3.class",
      "com/sys1/api/service5.class",
      "com/sys1/impl/service1Impl.class",
      "com/sys1/impl/service2Impl.class",
      "com/sys1/impl/service3ImplModified.class",
      "com/sys3/description.txt"
    );

    try (ZipOutputBuilder builder = new ZipOutputBuilderImpl(zipFile)) {

      assertEquals(allEntriesSorted, collect(builder.getEntryNames(), new ArrayList<>()));
      assertEquals(allEntriesSorted, collect(Iterators.filter(Iterators.recurse("/", builder::listEntries, false), n -> !isDirectoryName(n)), new ArrayList<>()).stream().sorted().collect(Collectors.toList()));
      assertEquals(allEntriesSorted, collect(Iterators.filter(Iterators.recurse("com/", builder::listEntries, false), n -> !isDirectoryName(n)), new ArrayList<>()).stream().sorted().collect(Collectors.toList()));

      builder.deleteEntry("com/sys2/");

      builder.deleteEntry("com/sys1/impl/service3Impl.class");
      builder.putEntry("com/sys1/impl/service3ImplModified.class", emptyContent);

      builder.putEntry("com/sys1/api/service1.class", new byte[] {42});
      builder.putEntry("com/sys1/api/service5.class", emptyContent);
      builder.putEntry("com/sys0/description.txt", descriptionContent.getBytes(StandardCharsets.UTF_8));
      builder.putEntry("com/sys3/description.txt", emptyContent);
      builder.close(true);
    }

    try (ZipOutputBuilder builder = new ZipOutputBuilderImpl(zipFile)) {
      assertEquals(allEntriesAfterModificationSorted, collect(builder.getEntryNames(), new ArrayList<>()));
      assertEquals(allEntriesAfterModificationSorted, collect(Iterators.filter(Iterators.recurse("/", builder::listEntries, false), n -> !isDirectoryName(n)), new ArrayList<>()).stream().sorted().collect(Collectors.toList()));
      assertEquals(allEntriesAfterModificationSorted, collect(Iterators.filter(Iterators.recurse("com/", builder::listEntries, false), n -> !isDirectoryName(n)), new ArrayList<>()).stream().sorted().collect(Collectors.toList()));

      byte[] modifiedContent = builder.getContent("com/sys1/api/service1.class");
      assertEquals(1, modifiedContent.length);
      assertEquals(42, modifiedContent[0]);
      assertEquals(descriptionContent, new String(builder.getContent("com/sys0/description.txt"), StandardCharsets.UTF_8));
    }

    Files.deleteIfExists(zipFile);
  }

  @Test
  public void testZipCopy() throws IOException {
    Path zipFile = myRootDir.resolve("test-archive-read.zip");
    Path zipFileWrite = myRootDir.resolve("test-archive-write.zip");
    assertFalse(Files.exists(zipFileWrite));

    List<String> allEntriesSorted = List.of(
      "com/sys1/api/service1.class",
      "com/sys1/api/service2.class",
      "com/sys1/api/service3.class",
      "com/sys1/impl/service1Impl.class",
      "com/sys1/impl/service2Impl.class",
      "com/sys1/impl/service3Impl.class",

      "com/sys2/api/service1.class",
      "com/sys2/api/service2.class",
      "com/sys2/api/service3.class",
      "com/sys2/impl/service1Impl.class",
      "com/sys2/impl/service2Impl.class",
      "com/sys2/impl/service3Impl.class"
    );
    byte[] emptyContent = new byte[0];

    // creation
    try (ZipOutputBuilder builder = new ZipOutputBuilderImpl(zipFile)) {
      assertTrue(isEmpty(builder.listEntries("")));
      for (String entry : allEntriesSorted) {
        builder.putEntry(entry, emptyContent);
      }
      builder.close(true);
    }

    // synchronize
    try (ZipOutputBuilder builder = new ZipOutputBuilderImpl(zipFile, zipFileWrite)) {
      builder.close(true);
    }

    assertTrue(Files.exists(zipFile));
    assertTrue(Files.exists(zipFileWrite));
    assertTrue(Files.isSameFile(zipFile, zipFileWrite));

    // reading
    try (ZipOutputBuilder builder = new ZipOutputBuilderImpl(zipFileWrite)) {

      assertEquals(allEntriesSorted, collect(builder.getEntryNames(), new ArrayList<>()));

      Set<String> entries_com = collect(builder.listEntries("com/"), new HashSet<>());
      assertEquals(Set.of("com/sys1/", "com/sys2/"), entries_com);

      Set<String> entries_com_sys1 = collect(builder.listEntries("com/sys1/"), new HashSet<>());
      assertEquals(Set.of("com/sys1/api/", "com/sys1/impl/"), entries_com_sys1);

      Set<String> entries_com_sys1_api = collect(builder.listEntries("com/sys1/api/"), new HashSet<>());
      assertEquals(Set.of("com/sys1/api/service1.class", "com/sys1/api/service2.class", "com/sys1/api/service3.class"), entries_com_sys1_api);
    }

    // modifying
    try (ZipOutputBuilder builder = new ZipOutputBuilderImpl(zipFile, zipFileWrite)) {
      builder.deleteEntry("com/sys2/");
      builder.putEntry("com/sys3/api/service4.class", new byte[] {43});
      builder.close(true);
    }

    assertTrue(Files.exists(zipFile));
    assertTrue(Files.exists(zipFileWrite));
    assertFalse(Files.isSameFile(zipFile, zipFileWrite));
    try (ZipOutputBuilder builder = new ZipOutputBuilderImpl(zipFile)) {
      assertEquals(allEntriesSorted, collect(builder.getEntryNames(), new ArrayList<>()));
    }
    try (ZipOutputBuilder builder = new ZipOutputBuilderImpl(zipFileWrite)) {
      List<String> expectedEntries = List.of(
        "com/sys1/api/service1.class",
        "com/sys1/api/service2.class",
        "com/sys1/api/service3.class",
        "com/sys1/impl/service1Impl.class",
        "com/sys1/impl/service2Impl.class",
        "com/sys1/impl/service3Impl.class",

        "com/sys3/api/service4.class"
      );
      assertEquals(expectedEntries, collect(builder.getEntryNames(), new ArrayList<>()));
    }

    Files.deleteIfExists(zipFile);
    Files.deleteIfExists(zipFileWrite);
  }
}
