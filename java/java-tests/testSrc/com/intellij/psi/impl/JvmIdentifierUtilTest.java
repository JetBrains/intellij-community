// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.psi.impl.cache.impl.idCache.JvmIdentifierUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class JvmIdentifierUtilTest {
  @Test
  void testIdentifierExtraction() {
    Assertions.assertEquals(List.of("foo"), getJvmIdParts("foo"));
    Assertions.assertEquals(List.of("foo", "bar"), getJvmIdParts("foo/bar"));
    Assertions.assertEquals(List.of("a", "bb", "ccc"), getJvmIdParts("a/bb/ccc"));
    Assertions.assertEquals(List.of("aaa", "bb", "c"), getJvmIdParts("aaa/bb/c"));
    Assertions.assertEquals(List.of("java", "lang", "String"), getJvmIdParts("[[Ljava/lang/String;"));
    Assertions.assertEquals(List.of("java", "lang", "Comparable", "java", "lang", "String", "java", "util", "List", "java", "lang", "Integer", "java", "lang", "Object"), getJvmIdParts("<T::Ljava/lang/Comparable<Ljava/lang/String;>;:Ljava/util/List<Ljava/lang/Integer;>;>Ljava/lang/Object;"));
    Assertions.assertEquals(List.of(
      "java", "util", "Map", "java", "lang", "String", "java", "util", "List", "java", "util", "List", "java", "util", "Comparator", "java", "lang", "String"
    ), getJvmIdParts("Ljava/util/Map<[Ljava/lang/String;Ljava/util/List<Ljava/util/List<+Ljava/util/Comparator<Ljava/lang/String;>;>;>;>;"));
    Assertions.assertEquals(List.of("foo$bar"), getJvmIdParts("foo$bar"));
    Assertions.assertNull(getJvmIdParts("1"));
    Assertions.assertNull(getJvmIdParts("1/bar"));
    Assertions.assertNull(getJvmIdParts("/"));
    Assertions.assertNull(getJvmIdParts("with/spa ce"));
    Assertions.assertNull(getJvmIdParts("asd*"));
    Assertions.assertNull(getJvmIdParts("My sentence with spaces"));
    Assertions.assertNull(getJvmIdParts(""));
    Assertions.assertNull(getJvmIdParts("foo/"));
    Assertions.assertNull(getJvmIdParts("/foo"));
  }

  @Nullable
  List<String> getJvmIdParts(String str) {
    ArrayList<CharSequence> parts = new ArrayList<>();
    if (JvmIdentifierUtil.collectJvmIdentifiers(str, (sequence1, start, end) -> parts.add(sequence1.subSequence(start, end)))) {
      return ContainerUtil.map(parts, sequence -> sequence.toString());
    }
    return null;
  }
}
