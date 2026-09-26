// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import junit.framework.TestCase.assertEquals
import org.junit.Test

internal class BazelLoadStatementManagerTest {

  private val manager = BazelLoadStatementManager()

  @Test
  fun `should accept a single load statements and return it`() {
    manager.insert("""load("@rules_jvm//:jvm.bzl", "jvm_library")""")

    assertEquals("""
      load("@rules_jvm//:jvm.bzl", "jvm_library")
    """.trimIndent(), manager.getResult())
  }

  @Test
  fun `should accept multiple load statements and return them`() {
    manager.insert("""load("@rules_jvm//:jvm.bzl", "jvm_library")""")
    manager.insert("""load("@rules_rust//:rust.bzl", "rust_library")""")

    assertEquals("""
      load("@rules_jvm//:jvm.bzl", "jvm_library")
      load("@rules_rust//:rust.bzl", "rust_library")
    """.trimIndent(), manager.getResult())
  }

  @Test
  fun `should merge multiple load statements with the same extension`() {
    manager.insert("""load("@rules_jvm//:jvm.bzl", "jvm_binary")""")
    manager.insert("""load("@rules_jvm//:jvm.bzl", "jvm_library")""")

    assertEquals("""
      load("@rules_jvm//:jvm.bzl", "jvm_binary", "jvm_library")
    """.trimIndent(), manager.getResult())
  }

  @Test
  fun `should sort load statements on output`() {
    manager.insert("""load("@rules_rust//:rust.bzl", "rust_library")""")
    manager.insert("""load("@rules_jvm//:jvm.bzl", "jvm_library")""")
    manager.insert("""load("@rules_jvm//:jvm.bzl", "jvm_binary")""")

    assertEquals("""
      load("@rules_jvm//:jvm.bzl", "jvm_binary", "jvm_library")
      load("@rules_rust//:rust.bzl", "rust_library")
    """.trimIndent(), manager.getResult())
  }

  @Test
  fun `should handle duplicated entries`() {
    manager.insert("""load("@rules_rust//:rust.bzl", "rust_library")""")
    manager.insert("""load("@rules_rust//:rust.bzl", "rust_library")""")

    assertEquals("""
      load("@rules_rust//:rust.bzl", "rust_library")
    """.trimIndent(), manager.getResult())
  }

  @Test
  fun `should support symbols renaming`() {
    manager.insert("""load("@rules_rust//:rust.bzl", my_rule = "rust_library")""")

    assertEquals("""
      load("@rules_rust//:rust.bzl", my_rule = "rust_library")
    """.trimIndent(), manager.getResult())
  }

  @Test
  fun `should sort symbols based on name`() {
    manager.insert("""load("@rules_jvm//:jvm.bzl", b = "jvm_library")""")
    manager.insert("""load("@rules_jvm//:jvm.bzl", "jvm_library")""")
    manager.insert("""load("@rules_jvm//:jvm.bzl", z = "jvm_library")""")
    manager.insert("""load("@rules_jvm//:jvm.bzl", a = "jvm_library")""")

    assertEquals("""
      load("@rules_jvm//:jvm.bzl", "jvm_library", a = "jvm_library", b = "jvm_library", z = "jvm_library")
    """.trimIndent(), manager.getResult())
  }
}
