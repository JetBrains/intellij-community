// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Run via `bazel test //:starter-bazel-tests-tests` from `community/platform/build-scripts/bazel`.
 *
 * The fixture `testData/starter-bazel-tests/starter-bazel-tests.json` is the hand input. The module graph is a map.
 */
class StarterBazelTestsTest {
  private val graph = FakeGraph(
    community = setOf("intellij.tools.ide.starter.build.server", "intellij.lambda.testFramework", "intellij.lambda.test.plugin", "community.tests"),
    direct = mapOf(
      "lambda.tests" to listOf("intellij.lambda.testFramework", "starter.product.idea"),
      "lambda.tests.plugin" to emptyList(),
      "intellij.lambda.testFramework" to listOf("intellij.tools.ide.starter.build.server", "starter.product.idea"),
      "plain.tests" to listOf("product.go-between", "starter.product.clion"),
      "product.go-between" to listOf("starter.product.idea", "intellij.tools.ide.starter.build.server"),
      "two.products" to listOf("starter.product.idea", "starter.product.clion", "intellij.tools.ide.starter.build.server"),
      "no.server" to listOf("starter.product.idea"),
      "no.product" to listOf("intellij.tools.ide.starter.build.server"),
      "community.tests" to listOf("intellij.tools.ide.starter.build.server", "starter.product.idea"),
      "mode.tests" to listOf("intellij.lambda.testFramework"),
      "mode.tests.monolith.plugin" to emptyList(),
      "intellij.tools.ide.starter.build.server" to emptyList(),
      "starter.product.idea" to emptyList(),
      "starter.product.clion" to emptyList(),
      "intellij.lambda.test.plugin" to emptyList(),
      "intellij.devkit" to emptyList(),
    ),
  )

  private val fixture: StarterBazelTests = checkNotNull(readStarterBazelTests(testDataPath().resolve("starter-bazel-tests.json")))

  @Test
  fun `the fixture reads both tables`() {
    assertThat(fixture.products).containsEntry("starter.product.idea", "idea").containsEntry("starter.product.clion", "CLion")
    assertThat(fixture.tests).containsEntry("plain.tests", listOf("intellij.devkit"))
  }

  @Test
  fun `a missing file gives no tables`() {
    assertThat(readStarterBazelTests(testDataPath().resolve("absent.json"))).isNull()
  }

  @Test
  fun `a lambda module starts the frontend and gets the lambda plugins`() {
    assertThat(resolve("lambda.tests" to emptyList())).containsExactly(
      "lambda.tests" to StarterTestTarget(product = "idea", frontend = true, contextPlugins = listOf("intellij.lambda.test.plugin", "lambda.tests.plugin")),
    )
  }

  @Test
  fun `the product of the nearest level wins over a deeper one`() {
    assertThat(resolve("plain.tests" to listOf("intellij.devkit"))).containsExactly(
      "plain.tests" to StarterTestTarget(product = "CLion", frontend = false, contextPlugins = listOf("intellij.devkit")),
    )
  }

  @Test
  fun `two products on one level fail`() {
    assertThatThrownBy { resolve("two.products" to emptyList()) }.hasMessageContaining("'two.products'").hasMessageContaining("one level")
  }

  @Test
  fun `a row fails for a module that a Starter test row cannot be`() {
    assertThatThrownBy { resolve("absent.tests" to emptyList()) }.hasMessageContaining("names no module")
    assertThatThrownBy { resolve("community.tests" to emptyList()) }.hasMessageContaining("community module")
    assertThatThrownBy { resolve("no.server" to emptyList()) }.hasMessageContaining("intellij.tools.ide.starter.build.server")
    assertThatThrownBy { resolve("no.product" to emptyList()) }.hasMessageContaining("no product module")
    assertThatThrownBy { resolve("mode.tests" to emptyList()) }.hasMessageContaining("mode.tests.monolith.plugin")
    assertThatThrownBy { resolve("plain.tests" to listOf("intellij.absent")) }.hasMessageContaining("intellij.absent")
  }

  @Test
  fun `the generated file holds the union of the context plugins of each product`() {
    val text = renderStarterBazelTestsGenerated(listOf(
      StarterTestTarget(product = "idea", frontend = true, contextPlugins = listOf("b", "a")),
      StarterTestTarget(product = "idea", frontend = false, contextPlugins = listOf("a", "c")),
      StarterTestTarget(product = "CLion", frontend = false, contextPlugins = emptyList()),
    ))
    assertThat(text).isEqualTo(
      """
      |{
      |    "generatedBy": "./build/jpsModelToBazel.cmd",
      |    "contextPlugins": {
      |        "CLion": [],
      |        "idea": [
      |            "a",
      |            "b",
      |            "c"
      |        ]
      |    }
      |}
      |""".trimMargin()
    )
  }

  private fun resolve(vararg rows: Pair<String, List<String>>): List<Pair<String, StarterTestTarget>> {
    return StarterBazelTests(products = fixture.products, tests = mapOf(*rows)).resolveStarterTestTargets(graph, "test").toList()
  }

  private class FakeGraph(private val community: Set<String>, private val direct: Map<String, List<String>>) : StarterTestModuleGraph {
    override fun hasModule(name: String): Boolean = name in direct || name in community

    override fun isCommunity(name: String): Boolean = name in community

    override fun directTestRuntime(name: String): List<String> = direct.get(name).orEmpty()

    override fun testRuntime(name: String): Set<String> {
      val result = LinkedHashSet<String>()
      val queue = ArrayDeque(directTestRuntime(name))
      while (queue.isNotEmpty()) {
        val module = queue.removeFirst()
        if (result.add(module)) {
          queue.addAll(directTestRuntime(module))
        }
      }
      result.remove(name)
      return result
    }
  }

  private fun testDataPath(): Path {
    val marker = System.getenv(TEST_DATA_MARKER_ENV) ?: error("Missing $TEST_DATA_MARKER_ENV env variable in bazel test environment")
    val result = Path.of(System.getenv("TEST_SRCDIR") ?: error("Missing TEST_SRCDIR"), marker).parent
    check(Files.isDirectory(result)) { "No test data directory $result" }
    return result
  }

  private companion object {
    const val TEST_DATA_MARKER_ENV = "STARTER_BAZEL_TESTS_TEST_DATA_MARKER"
  }
}
