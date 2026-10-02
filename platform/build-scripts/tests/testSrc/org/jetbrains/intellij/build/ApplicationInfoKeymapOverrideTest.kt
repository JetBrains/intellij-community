// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * The frontend application info states the keymap fact of the host product.
 *
 * The client and the expected text are the `keymap` fixture of the Rust twin
 * (`community/build/dev-dist-tools/bins/plugin-descriptor-writer/testdata/application_info`).
 */
class ApplicationInfoKeymapOverrideTest {
  @Test
  fun `the keymap element follows the host`() {
    assertThat(applyOverride(reassignAltClickToMultipleCarets = "true")).isEqualTo("""
      <component xmlns="http://jetbrains.org/intellij/schema/application-info" xmlns:other="urn:other">
        <names product="JetBrains Client" fullname="Product" />
        <version major="263" minor="2" />
        <build number="__BUILD_NUMBER__" />
        <other:keymap reassignAltClickToMultipleCarets="kept" />
        <keymap reassignAltClickToMultipleCarets="true" />
      </component>
    """.trimIndent())
  }

  @Test
  fun `a host without the keymap fact removes the element`() {
    assertThat(applyOverride(reassignAltClickToMultipleCarets = null)).isEqualTo("""
      <component xmlns="http://jetbrains.org/intellij/schema/application-info" xmlns:other="urn:other">
        <names product="JetBrains Client" fullname="Product" />
        <version major="263" minor="2" />
        <build number="__BUILD_NUMBER__" />
        <other:keymap reassignAltClickToMultipleCarets="kept" />
      </component>
    """.trimIndent())
  }
}

@Suppress("DEPRECATION")
private fun applyOverride(reassignAltClickToMultipleCarets: String?): String {
  val client = """
    <component xmlns="http://jetbrains.org/intellij/schema/application-info" xmlns:other="urn:other">
      <keymap reassignAltClickToMultipleCarets="false" />
      <names product="JetBrains Client" />
      <version />
      <build number="__BUILD_NUMBER__" />
      <other:keymap reassignAltClickToMultipleCarets="kept" />
      <keymap reassignAltClickToMultipleCarets="second" />
    </component>
  """.trimIndent()
  return applyApplicationInfoOverrides(
    originalPatchedAppInfo = client,
    isEapOverride = null,
    suffixOverride = null,
    appInfoXmlPath = Path.of("JetBrainsClientApplicationInfo.xml"),
    appInfoOverride = ProductProperties.ApplicationInfoOverrides(
      fullProductName = "Product",
      editionName = null,
      motto = null,
      eap = null,
      majorVersion = "263",
      minorVersion = "2",
      microVersion = null,
      patchVersion = null,
      fullVersionFormat = null,
      versionSuffix = null,
      majorReleaseDate = null,
      reassignAltClickToMultipleCarets = reassignAltClickToMultipleCarets,
    ),
    branchName = null,
  )
}
