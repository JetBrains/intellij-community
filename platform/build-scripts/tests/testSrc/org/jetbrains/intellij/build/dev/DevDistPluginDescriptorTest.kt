// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import com.intellij.openapi.util.JDOMUtil
import org.jdom.Namespace
import org.jetbrains.intellij.build.impl.BazelBuildInputs
import org.jetbrains.intellij.build.impl.checkProducedPluginDescriptor
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The two modes of `DevDistPluginDescriptorMain`, and the guard on a produced descriptor.
 *
 * The resolver reads declared files only. A descriptor the request does not declare fails the run instead of loading a
 * project model. The application info mode stamps the client template from three declared files. The two refusals of
 * the guard and the no-manifest case are its negative control, and the accepted arm is the reference. A check that
 * cannot fail is not evidence, which is ADR 0006 rule 2.
 */
class DevDistPluginDescriptorTest {
  @Test
  fun `an embedded product descriptor resolves only declared files`(@TempDir dir: Path) {
    val text = resolveEmbeddedProductDescriptorFromPlan(embeddedProductFixture(dir)).decodeToString()

    assertThat(text)
      .doesNotContain("xi:include")
      .contains(
        "<module name=\"$BACKEND\"><![CDATA[",
        "serviceImplementation=\"com.example.BackendService\"",
        "separate-jar=\"true\"",
        "<module name=\"$FRONTEND\"><![CDATA[",
      )
  }

  @Test
  fun `an undeclared embedded product descriptor fails without a project model`(@TempDir dir: Path) {
    val request = embeddedProductFixture(dir)
    val incomplete = DevDistEmbeddedProductDescriptorRequest(
      output = request.output,
      source = request.source,
      descriptors = request.descriptors - "$BACKEND.xml",
      descriptorsInJar = request.descriptorsInJar,
      modules = request.modules,
      separateJarModules = request.separateJarModules,
    )

    assertThatThrownBy { resolveEmbeddedProductDescriptorFromPlan(incomplete) }
      .isInstanceOf(UnsupportedOperationException::class.java)
      .hasMessageContaining("needs a JPS project model")
  }

  @Test
  fun `the embedded product argument grammar round trips`(@TempDir dir: Path) {
    val request = parseDevDistEmbeddedProductDescriptorRequest(listOf(
      "--embedded-product",
      "--out=${dir.resolve("out.xml")}",
      "--source=${dir.resolve("JetBrainsClientPlugin.xml")}",
      "--descriptor=intellij.example.backend.xml=${dir.resolve("backend.xml")}",
      "--descriptor-in-jar=META-INF/extensions.xml=${dir.resolve("first.jar")}",
      "--descriptor-in-jar=META-INF/extensions.xml=${dir.resolve("second.jar")}",
      "--module=intellij.frontend.split.customization",
      "--module=intellij.example.backend",
      "--separate-jar=intellij.example.backend",
    ))

    assertThat(request.descriptors).containsOnlyKeys("intellij.example.backend.xml")
    assertThat(request.descriptorsInJar.getValue("META-INF/extensions.xml"))
      .containsExactly(dir.resolve("first.jar"), dir.resolve("second.jar"))
    assertThat(request.modules).containsExactly("intellij.frontend.split.customization", "intellij.example.backend")
    assertThat(request.separateJarModules).containsExactly("intellij.example.backend")
  }

  @Test
  fun `a request states exactly one mode`() {
    assertThat(devDistPluginDescriptorMode(listOf("--application-info", "--out=x"))).isEqualTo(APPLICATION_INFO_MODE)
    assertThatThrownBy { devDistPluginDescriptorMode(listOf("--out=x")) }.isInstanceOf(IllegalArgumentException::class.java)
    assertThatThrownBy { devDistPluginDescriptorMode(listOf("--embedded-product", "--application-info")) }
      .isInstanceOf(IllegalArgumentException::class.java)
  }

  /**
   * The `--application-info` mode: the client template takes the build number as `JBC-<build>`, and the product
   * application info supplies the names, the version and the release date. The edition stays the client's own.
   */
  @Test
  fun `the frontend application info takes the product values`(@TempDir dir: Path) {
    val request = parseDevDistFrontendApplicationInfoRequest(listOf(
      "--application-info",
      "--out=${dir.resolve("out/JetBrainsClientApplicationInfo.xml")}",
      "--client-application-info=${write(dir.resolve("client.xml"), CLIENT_APPLICATION_INFO)}",
      "--product-application-info=${write(dir.resolve("product.xml"), PRODUCT_APPLICATION_INFO)}",
      "--build-number=${write(dir.resolve("build.txt"), "263.SNAPSHOT\n")}",
      "--branch-name=feature/client",
    ))
    assertThat(request.eapOverride).isNull()
    assertThat(request.versionSuffixOverride).isNull()
    assertThat(request.nightly).isFalse()

    val result = JDOMUtil.load(resolveFrontendApplicationInfo(request))
    val namespace = Namespace.getNamespace("http://jetbrains.org/intellij/schema/application-info")
    val version = result.getChild("version", namespace)
    val build = result.getChild("build", namespace)
    val names = result.getChild("names", namespace)
    assertThat(build.getAttributeValue("number")).isEqualTo("JBC-263.SNAPSHOT")
    assertThat(build.getAttributeValue("date")).isEqualTo("__BUILD_DATE__")
    assertThat(build.getAttributeValue("majorReleaseDate")).isEqualTo("20260909")
    assertThat(build.getAttributeValue("branchName")).isEqualTo("feature/client")
    assertThat(names.getAttributeValue("fullname")).isEqualTo("IntelliJ IDEA Ultimate")
    assertThat(names.getAttributeValue("edition")).isNull()
    assertThat(names.getAttributeValue("motto")).isEqualTo("Code")
    assertThat(version.getAttributeValue("micro")).isEqualTo("2")
    assertThat(version.getAttributeValue("patch")).isEqualTo("1")
  }

  @Test
  fun `the frontend application info refuses an empty build number`(@TempDir dir: Path) {
    val request = DevDistFrontendApplicationInfoRequest(
      output = dir.resolve("out.xml"),
      clientApplicationInfo = write(dir.resolve("client.xml"), CLIENT_APPLICATION_INFO),
      productApplicationInfo = write(dir.resolve("product.xml"), PRODUCT_APPLICATION_INFO),
      buildNumber = write(dir.resolve("build.txt"), "\n"),
    )

    assertThatThrownBy { resolveFrontendApplicationInfo(request) }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("build number is empty")
  }

  @Test
  fun `the frontend application info refuses a product without a unique version element`(@TempDir dir: Path) {
    val request = DevDistFrontendApplicationInfoRequest(
      output = dir.resolve("out.xml"),
      clientApplicationInfo = write(dir.resolve("client.xml"), CLIENT_APPLICATION_INFO),
      productApplicationInfo = write(dir.resolve("product.xml"), PRODUCT_APPLICATION_INFO.replace("<version ", "<edition ")),
      buildNumber = write(dir.resolve("build.txt"), "263.SNAPSHOT"),
    )

    assertThatThrownBy { resolveFrontendApplicationInfo(request) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("no unique version element")
  }

  private fun write(file: Path, text: String): Path {
    Files.writeString(file, text)
    return file
  }

  /**
   * The seam a fragment reads the produced descriptor through, and the guard on what it read.
   *
   * A fragment that reads a produced descriptor cannot compare it against its own patch. The guard checks the stamps
   * instead: the version and the compatibility range must be this assembly's. The two refusals below are its negative
   * control, and this accepted arm is the reference.
   */
  @Test
  fun `a produced descriptor whose stamps agree is accepted`() {
    checkProducedPluginDescriptor(
      mainModule = MAIN_MODULE,
      content = PRODUCED_DESCRIPTOR,
      pluginVersion = "263.99999999.0",
      compatibleSinceUntil = "263.SNAPSHOT" to "263.SNAPSHOT",
    )
  }

  @Test
  fun `a produced descriptor whose version is not this assembly's is refused`() {
    assertThatThrownBy {
      checkProducedPluginDescriptor(
        mainModule = MAIN_MODULE,
        content = PRODUCED_DESCRIPTOR,
        pluginVersion = "263.99999998.0",
        compatibleSinceUntil = "263.SNAPSHOT" to "263.SNAPSHOT",
      )
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining(MAIN_MODULE)
      .hasMessageContaining("263.99999998.0")
  }

  @Test
  fun `a produced descriptor whose compatibility range is not this assembly's is refused`() {
    assertThatThrownBy {
      checkProducedPluginDescriptor(
        mainModule = MAIN_MODULE,
        content = PRODUCED_DESCRIPTOR,
        pluginVersion = "263.99999999.0",
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

  private fun embeddedProductFixture(dir: Path): DevDistEmbeddedProductDescriptorRequest {
    val source = dir.resolve("JetBrainsClientPlugin.xml")
    val productModules = dir.resolve("product-modules.xml")
    val backendDescriptor = dir.resolve("$BACKEND.xml")
    val frontendDescriptor = dir.resolve("$FRONTEND.xml")
    val emptyJar = dir.resolve("empty.jar")
    val descriptorJar = dir.resolve("descriptors.jar")
    Files.writeString(source, EMBEDDED_PRODUCT_SOURCE)
    Files.writeString(productModules, EMBEDDED_PRODUCT_MODULES)
    Files.writeString(backendDescriptor, EMBEDDED_BACKEND_DESCRIPTOR)
    Files.writeString(frontendDescriptor, """<idea-plugin package="$FRONTEND" />""")
    writeJar(emptyJar, emptyMap())
    writeJar(descriptorJar, mapOf("META-INF/backend-extensions.xml" to EMBEDDED_BACKEND_EXTENSIONS))
    return DevDistEmbeddedProductDescriptorRequest(
      output = dir.resolve("out/JetBrainsClientPlugin.xml"),
      source = source,
      descriptors = mapOf(
        "META-INF/product-modules.xml" to productModules,
        "$BACKEND.xml" to backendDescriptor,
        "$FRONTEND.xml" to frontendDescriptor,
      ),
      descriptorsInJar = mapOf("META-INF/backend-extensions.xml" to listOf(emptyJar, descriptorJar)),
      modules = listOf("intellij.frontend.split.customization", BACKEND, FRONTEND),
      separateJarModules = setOf(BACKEND),
    )
  }

  private fun writeJar(file: Path, entries: Map<String, String>) {
    ZipOutputStream(Files.newOutputStream(file)).use { output ->
      for ((name, content) in entries) {
        output.putNextEntry(ZipEntry(name))
        output.write(content.encodeToByteArray())
        output.closeEntry()
      }
    }
  }
}

private const val MAIN_MODULE = "intellij.example"
private const val BACKEND = "intellij.example.backend"
private const val FRONTEND = "intellij.example.frontend"

/** A produced descriptor whose stamps are the fixture assembly's: the version and the compatibility range. */
private val PRODUCED_DESCRIPTOR = """
  <idea-plugin>
    <id>com.example</id>
    <version>263.99999999.0</version>
    <idea-version since-build="263.SNAPSHOT" until-build="263.SNAPSHOT" />
    <content>
      <module name="$BACKEND"><![CDATA[<idea-plugin package="$BACKEND" />]]></module>
      <module name="$FRONTEND"><![CDATA[<idea-plugin package="$FRONTEND" />]]></module>
    </content>
  </idea-plugin>
""".trimIndent()

private val EMBEDDED_PRODUCT_SOURCE = """
  <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
    <id>com.intellij</id>
    <xi:include href="/META-INF/product-modules.xml"/>
  </idea-plugin>
""".trimIndent()

private val EMBEDDED_PRODUCT_MODULES = """
  <idea-plugin>
    <content>
      <module name="$BACKEND"/>
      <module name="$FRONTEND"/>
    </content>
  </idea-plugin>
""".trimIndent()

private val EMBEDDED_BACKEND_DESCRIPTOR = """
  <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude" package="$BACKEND">
    <xi:include href="/META-INF/backend-extensions.xml"/>
  </idea-plugin>
""".trimIndent()

private val EMBEDDED_BACKEND_EXTENSIONS = """
  <idea-plugin>
    <extensions defaultExtensionNs="com.intellij">
      <applicationService serviceImplementation="com.example.BackendService"/>
    </extensions>
  </idea-plugin>
""".trimIndent()

private val CLIENT_APPLICATION_INFO = """
  <component xmlns="http://jetbrains.org/intellij/schema/application-info">
    <version major="2026" minor="3" eap="true"/>
    <company name="JetBrains s.r.o." url="https://www.jetbrains.com/"/>
    <build number="JBC-__BUILD__" date="__BUILD_DATE__"/>
    <names product="JetBrainsClient" fullname="JetBrains Client" script="jetbrains_client" motto="Client"/>
  </component>
""".trimIndent()

private val PRODUCT_APPLICATION_INFO = """
  <component xmlns="http://jetbrains.org/intellij/schema/application-info">
    <version major="2026" minor="3" micro="2" patch="1" full="{0}.{1}.{2}" suffix="EAP" eap="true"/>
    <build number="IU-__BUILD__" majorReleaseDate="20260909"/>
    <names product="IDEA" fullname="IntelliJ IDEA Ultimate" edition="ultimate" motto="Code"/>
  </component>
""".trimIndent()
