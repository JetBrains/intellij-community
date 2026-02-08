// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel;

import com.intellij.tools.build.bazel.jvmIncBuilder.impl.KotlinCriUtilKt;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class KotlinCriTest {
  @Test
  public void testNormalizeJvmName() {
    assertNormalized("com.example.Foo", "com/example/Foo");
    assertNormalized("com.example.Foo.Bar", "com/example/Foo$Bar");
    assertNormalized("com.example.Foo", "com/example/Foo$1");
    assertNormalized("com.example.Foo.$Lambda", "com/example/Foo$$Lambda$3");
    assertNormalized("com.example.Foo", "com/example/Foo$1$2");
    assertNormalized("com.example.Foo.Bar.Baz", "com/example/Foo$Bar$Baz");
    assertNormalized("com.example.Foo.Bar", "com/example/Foo$Bar$1");
    assertNormalized("com.example.Foo.$Lambda", "com/example/Foo$$Lambda$123");
    assertNormalized("com.example.Foo.$Bar", "com/example/Foo$$Bar");
    assertNormalized("", "");
    assertNormalized("Foo", "Foo");
    assertNormalized("com.example.Foo.$Lambda", "com/example/Foo$$Lambda$1$2");
  }

  private static void assertNormalized(String expected, String actual) {
    assertEquals(expected, KotlinCriUtilKt.normalizeJvmNameForTests(actual));
  }
}