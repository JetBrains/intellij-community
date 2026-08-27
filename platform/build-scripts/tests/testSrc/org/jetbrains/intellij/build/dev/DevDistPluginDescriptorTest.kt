// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.impl.BazelBuildInputs
import org.jetbrains.intellij.build.impl.checkProducedPluginDescriptor
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * The plan-driven half of the per-plugin descriptor action, and the three negative controls of the M2 design.
 *
 * Each control damages the **plan** against an undamaged descriptor, and each is checked both ways: damaged, which must
 * change the text or be refused, and undamaged, which must produce the reference text. A comparison that cannot fail is
 * not evidence, which is ADR 0006 rule 2.
 *
 * The byte comparison against a real assembly is `./build/dev-dist.cmd descriptors`. This test needs no distribution
 * build, so it catches a regression in the plan-driven stage long before that gate runs.
 */
class DevDistPluginDescriptorTest {
  @Test
  fun `the patch runs with no project model`(@TempDir dir: Path) {
    val text = patch(fixture(dir))
    assertThat(text).isEqualTo(REFERENCE)
  }

  /**
   * Control (a): the plan loses one content-module name.
   *
   * The action removes every `<module/>` the plan does not name, because that is how the assembly's `ContentModuleFilter`
   * refuses an optional module. So a plan that lost a name loses the `<module/>` and its embedded body.
   */
  @Test
  fun `a dropped content module changes the text`(@TempDir dir: Path) {
    val damaged = patch(fixture(dir, contentModules = listOf(BACKEND)))

    assertThat(damaged).isNotEqualTo(REFERENCE)
    assertThat(damaged).doesNotContain(FRONTEND)
    assertThat(patch(fixture(dir))).isEqualTo(REFERENCE)
  }

  /**
   * Control (b): the plan states the two content modules in the other order.
   *
   * This one is **refused** rather than applied, and that is the honest shape of it. The surviving order is the
   * descriptor's own order, and no plan can change where an XML child sits. What the plan's order is for is exactly this
   * guard: a plan whose order disagrees with the descriptor is a plan that is describing another descriptor.
   */
  @Test
  fun `a reordered content list is refused`(@TempDir dir: Path) {
    assertThatThrownBy { patch(fixture(dir, contentModules = listOf(FRONTEND, BACKEND))) }
      .isInstanceOf(RuntimeException::class.java)
      .hasMessageContaining("Could not patch descriptor (module=$MAIN_MODULE)")

    assertThat(patch(fixture(dir))).isEqualTo(REFERENCE)
  }

  /**
   * Control (c): the plan flips one `separate-jar` verdict.
   *
   * One attribute of one embedded descriptor, and nothing else. The assembly decides it from
   * `JarPackagerDependencyHelper.isPluginModulePackedIntoSeparateJar`, which reads the JPS project model, so it has to
   * be a plan field here.
   */
  @Test
  fun `a flipped separate-jar verdict changes one attribute`(@TempDir dir: Path) {
    val damaged = patch(fixture(dir, separateJarModules = setOf(BACKEND)))

    assertThat(damaged).isNotEqualTo(REFERENCE)
    assertThat(damaged).contains("separate-jar=\"true\"")
    assertThat(REFERENCE).doesNotContain("separate-jar")
    assertThat(patch(fixture(dir))).isEqualTo(REFERENCE)
  }

  /** A descriptor the plan does not declare must fail loudly, and never load a project model to find it. */
  @Test
  fun `an undeclared content module descriptor fails loudly`(@TempDir dir: Path) {
    assertThatThrownBy { patch(fixture(dir, declareContentModuleDescriptors = false)) }
      .isInstanceOf(RuntimeException::class.java)
      .hasStackTraceContaining("needs a JPS project model")
  }

  /** The version and the compatibility range are derived from the declared build number and the pinned build date. */
  @Test
  fun `the stamps are derived from the build number file`(@TempDir dir: Path) {
    assertThat(patch(fixture(dir))).contains(
      "<version>263.20260101.0</version>",
      """<idea-version since-build="263.SNAPSHOT" until-build="263.SNAPSHOT" />""",
    )
  }

  /** The argument grammar the rule writes into a parameter file, read back into the request the body runs. */
  @Test
  fun `the argument grammar round trips`(@TempDir dir: Path) {
    val request = parseDevDistPluginDescriptorRequest(listOf(
      "--out=${dir.resolve("out.xml")}",
      "--main-module=intellij.cwm",
      "--source=${dir.resolve("plugin.xml")}",
      "--build-number-file=${dir.resolve("build.txt")}",
      "--build-date-seconds=1767225600",
      "--release-date=20260101",
      "--release-version=2026300",
      "--eap=true",
      "--exact-version=true",
      "--retain-product-descriptor=true",
      "--embed-content-modules=false",
      "--content-module=intellij.a",
      "--content-module=intellij.b",
      "--separate-jar=intellij.b",
      "--plugin-descriptor=intellij.a.xml=${dir.resolve("a.xml")}",
      "--platform-descriptor=x/y.xml=${dir.resolve("y.xml")}",
      "--plugin-module=intellij.cwm",
      "--platform-module=intellij.platform.ide.impl",
    ))

    assertThat(request.mainModule).isEqualTo("intellij.cwm")
    // Derived, and the plan states a deviation only. `intellij.cwm` takes `cwm-plugin` in the real plan; here nothing
    // states one, so the derived name is what the request carries.
    assertThat(request.directoryName).isEqualTo("cwm")
    assertThat(request.mainJarName).isEqualTo("cwm.jar")
    assertThat(request.isEap).isTrue()
    assertThat(request.exactVersion).isTrue()
    assertThat(request.retainProductDescriptor).isTrue()
    assertThat(request.embedsContentModules).isFalse()
    assertThat(request.contentModules).containsExactly("intellij.a", "intellij.b")
    assertThat(request.separateJarModules).containsExactly("intellij.b")
    assertThat(request.pluginDescriptors).containsOnlyKeys("intellij.a.xml")
    assertThat(request.platformDescriptors).containsOnlyKeys("x/y.xml")
    assertThat(request.pluginModules).containsExactly("intellij.cwm")
    assertThat(request.platformModules).containsExactly("intellij.platform.ide.impl")
  }

  @Test
  fun `an unknown option is refused`() {
    assertThatThrownBy { parseDevDistPluginDescriptorRequest(listOf("--tomorrow=1")) }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("--tomorrow")
  }

  /**
   * The seam a fragment reads the produced descriptor through, and the guard on what it read.
   *
   * The guard is what replaces the byte comparison the descriptor gate loses for these plugins: once a fragment reads
   * the produced file, the gate holds that plugin out rather than compare the file against a record of itself. So the
   * three cases below are the negative control of the guard, and the accepted arm is the reference.
   */
  @Test
  fun `a produced descriptor whose stamps agree is accepted`() {
    checkProducedPluginDescriptor(
      mainModule = MAIN_MODULE,
      content = REFERENCE,
      pluginVersion = "263.20260101.0",
      compatibleSinceUntil = "263.SNAPSHOT" to "263.SNAPSHOT",
    )
  }

  @Test
  fun `a produced descriptor whose version is not this assembly's is refused`() {
    assertThatThrownBy {
      checkProducedPluginDescriptor(
        mainModule = MAIN_MODULE,
        content = REFERENCE,
        pluginVersion = "263.20260102.0",
        compatibleSinceUntil = "263.SNAPSHOT" to "263.SNAPSHOT",
      )
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining(MAIN_MODULE)
      .hasMessageContaining("263.20260102.0")
  }

  @Test
  fun `a produced descriptor whose compatibility range is not this assembly's is refused`() {
    assertThatThrownBy {
      checkProducedPluginDescriptor(
        mainModule = MAIN_MODULE,
        content = REFERENCE,
        pluginVersion = "263.20260101.0",
        compatibleSinceUntil = "263.1" to "263.*",
      )
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("since-build='263.1'")
  }

  /**
   * With no input manifest there is no declaration to read, so every plugin takes the computed path.
   *
   * Required and not cosmetic: the key is not a Bazel label, so the runfiles fallback the other probe takes would throw
   * on it. The in-process dev assembly runs with no manifest.
   */
  @Test
  fun `no produced descriptor is found without an input manifest`() {
    assertThat(BazelBuildInputs.producedPluginDescriptorIfDeclared(MAIN_MODULE)).isNull()
  }

  private fun patch(request: DevDistPluginDescriptorRequest): String = runBlocking {
    patchPluginDescriptorFromPlan(request)
  }

  private fun fixture(
    dir: Path,
    contentModules: List<String> = listOf(BACKEND, FRONTEND),
    separateJarModules: Set<String> = emptySet(),
    declareContentModuleDescriptors: Boolean = true,
  ): DevDistPluginDescriptorRequest {
    Files.writeString(dir.resolve("build.txt"), "263.SNAPSHOT\n")
    Files.writeString(dir.resolve("plugin.xml"), SOURCE)
    Files.writeString(dir.resolve("$BACKEND.xml"), """<idea-plugin package="$BACKEND" />""")
    Files.writeString(dir.resolve("$FRONTEND.xml"), """<idea-plugin package="$FRONTEND" />""")
    return DevDistPluginDescriptorRequest(
      output = dir.resolve("out/plugin.xml"),
      mainModule = MAIN_MODULE,
      directoryName = "example",
      mainJarName = "example.jar",
      source = dir.resolve("plugin.xml"),
      buildNumberFile = dir.resolve("build.txt"),
      buildDateInSeconds = 1767225600,
      releaseDate = "20260101",
      releaseVersion = "2026300",
      isEap = true,
      exactVersion = false,
      retainProductDescriptor = false,
      embedsContentModules = true,
      contentModules = contentModules,
      separateJarModules = separateJarModules,
      pluginDescriptors = if (declareContentModuleDescriptors) {
        mapOf(
          "$BACKEND.xml" to dir.resolve("$BACKEND.xml"),
          "$FRONTEND.xml" to dir.resolve("$FRONTEND.xml"),
        )
      }
      else {
        emptyMap()
      },
      platformDescriptors = emptyMap(),
      pluginModules = listOf(MAIN_MODULE),
      platformModules = emptyList(),
    )
  }
}

private const val MAIN_MODULE = "intellij.example"
private const val BACKEND = "intellij.example.backend"
private const val FRONTEND = "intellij.example.frontend"

private val SOURCE = """
  <idea-plugin>
    <id>com.example</id>
    <content>
      <module name="$BACKEND"/>
      <module name="$FRONTEND"/>
    </content>
  </idea-plugin>
""".trimIndent()

private val REFERENCE = """
  <idea-plugin>
    <id>com.example</id>
    <version>263.20260101.0</version>
    <idea-version since-build="263.SNAPSHOT" until-build="263.SNAPSHOT" />
    <content>
      <module name="$BACKEND"><![CDATA[<idea-plugin package="$BACKEND" />]]></module>
      <module name="$FRONTEND"><![CDATA[<idea-plugin package="$FRONTEND" />]]></module>
    </content>
  </idea-plugin>
""".trimIndent()
