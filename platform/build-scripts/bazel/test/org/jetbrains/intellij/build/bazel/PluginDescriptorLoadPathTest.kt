// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins every shape of `LoadPathUtil.toLoadPath`, the rule three producers of a descriptor request state.
 *
 * The plan generator calls the authority to compose a report row's load path, and this converter looks the row up by
 * the load path it composes itself. A prefix one side knows and the other does not makes the row unreachable, and the
 * `xi:include` then contributes no content module.
 *
 * A literal table and not a call, because the authority is in `intellij.platform.pluginSystem.parser.impl`, which this
 * converter does not depend on.
 */
internal class PluginDescriptorLoadPathTest {
  @Test
  fun `a leading slash is taken verbatim`() {
    assertEquals("META-INF/example.xml", toLoadPath("/META-INF/example.xml"))
    assertEquals("other/example.xml", toLoadPath("/other/example.xml"))
  }

  @Test
  fun `a module descriptor names a resource root`() {
    assertEquals("intellij.example.xml", toLoadPath("intellij.example.xml"))
    assertEquals("fleet.example.xml", toLoadPath("fleet.example.xml"))
    assertEquals("kotlin.example.xml", toLoadPath("kotlin.example.xml"))
  }

  @Test
  fun `every other href is relative to META-INF`() {
    assertEquals("META-INF/example.xml", toLoadPath("example.xml"))
    assertEquals("META-INF/sub/example.xml", toLoadPath("sub/example.xml"))
  }
}
