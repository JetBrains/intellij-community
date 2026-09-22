package com.intellij.tools.build.bazel.ijPluginPackager

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

internal class ModuleOutputManifestTest {
  private val toolManifest = "Manifest-Version: 1.0\r\nCreated-By: singlejar\r\nTarget-Label: @@community+//libraries/groovy:groovy\r\n" +
                             "Injecting-Rule-Kind: kt_jvm_library\r\n\r\n"

  @Test
  fun dropsManifestWrittenByAJarToolOnly() {
    assertNull(patch(toolManifest))
    assertNull(patch("Manifest-Version: 1.0\r\nCreated-By: io.bazel.rules.kotlin\r\n\r\n"))
  }

  @Test
  fun keepsAttributesOfTheModuleAndRemovesTheToolAttributes() {
    val manifest = "Manifest-Version: 1.0\r\nCreated-By: singlejar\r\nTarget-Label: @@community+//plugins/java-decompiler/engine:engine\r\n" +
                   "Injecting-Rule-Kind: kt_jvm_library\r\nMain-Class: org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler\r\n" +
                   "Automatic-Module-Name: org.jetbrains.java.decompiler\r\n\r\n"
    assertEquals(
      "Manifest-Version: 1.0\r\nMain-Class: org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler\r\n" +
      "Automatic-Module-Name: org.jetbrains.java.decompiler\r\n\r\n",
      patch(manifest),
    )
  }

  @Test
  fun removesTheContinuationLinesOfAToolAttribute() {
    val manifest = "Manifest-Version: 1.0\r\nTarget-Label: @@community+//platform/a/very/long/package/path/that/needs/wrapping/in/the/\r\n" +
                   " manifest:target\r\nPremain-Class: agent.Main\r\n\r\n"
    assertEquals("Manifest-Version: 1.0\r\nPremain-Class: agent.Main\r\n\r\n", patch(manifest))
  }

  @Test
  fun keepsAManifestWithPerEntrySections() {
    val manifest = "Manifest-Version: 1.0\r\nCreated-By: singlejar\r\n\r\nName: com/example/Api.class\r\nSealed: true\r\n\r\n"
    assertEquals("Manifest-Version: 1.0\r\n\r\nName: com/example/Api.class\r\nSealed: true\r\n\r\n", patch(manifest))
  }

  @Test
  fun returnsAManifestWithoutToolAttributesAsIs() {
    for (manifest in listOf(
      "Premain-Class: com.intellij.rt.execution.application.AppMainV2\$Agent\n",
      "Manifest-Version: 1.0\n",
      "Manifest-Version: 1.0\nMain-Class: org.jetbrains.ether.StorageDumper\n\n",
    )) {
      val data = ByteBuffer.wrap(manifest.toByteArray())
      assertSame(data, patchModuleOutputManifest(data), manifest)
    }
  }

  private fun patch(manifest: String): String? {
    return patchModuleOutputManifest(ByteBuffer.wrap(manifest.toByteArray()))?.let { Charsets.UTF_8.decode(it).toString() }
  }
}
