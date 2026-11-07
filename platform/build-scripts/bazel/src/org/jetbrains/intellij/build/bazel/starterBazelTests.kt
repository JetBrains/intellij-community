// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.bazel

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Files
import java.nio.file.Path
import java.util.TreeMap
import java.util.TreeSet

/** The hand-edited input of the Starter test targets, relative to the ultimate root. */
internal const val STARTER_BAZEL_TESTS_RELATIVE_PATH: String = "build/starter-bazel-tests.json"

/** The context plugins of each product that the Starter test targets install, relative to the ultimate root. */
internal const val STARTER_BAZEL_TESTS_GENERATED_RELATIVE_PATH: String = "build/starter-bazel-tests.generated.json"

/** The `.bzl` file and the symbol of the test target of a Starter test module. */
internal const val STARTER_TEST_BZL: String = "//build:starter_test.bzl"
internal const val STARTER_TEST_MACRO: String = "starter_test"

/** The module that every Starter test reaches in its test runtime: the dev build server of IDE Starter. */
private const val STARTER_BUILD_SERVER_MODULE = "intellij.tools.ide.starter.build.server"

/** The lambda test framework. A module with it in its test runtime is a lambda module, which runs in the split mode. */
private const val LAMBDA_TEST_FRAMEWORK_MODULE = "intellij.lambda.testFramework"

/** The plugin that every lambda test loads. */
private const val LAMBDA_TEST_PLUGIN_MODULE = "intellij.lambda.test.plugin"

/**
 * The suffixes of the plugins of a lambda module with one plugin per mode, see `LambdaTestPluginHolder.modeSplitPlugins`.
 * A context plugin is loaded in every mode, so such a module cannot have a `starter_test`.
 */
private val MODE_SPLIT_PLUGIN_SUFFIXES = listOf("monolith.plugin", "backend.split.plugin", "frontend.split.plugin")

/**
 * The facts of `build/starter-bazel-tests.json`.
 *
 * [products] maps an IDE Starter product module to the `build/dev-build.json` key of its product. [tests] is the
 * allowlist: a test module gets `starter_test` only when it has a row, and the row lists the context plugins that its
 * tests need beyond the product.
 */
internal class StarterBazelTests(
  @JvmField val products: Map<String, String>,
  @JvmField val tests: Map<String, List<String>>,
)

/**
 * The attributes of the `starter_test` of one test module.
 *
 * [product] is the product key. [frontend] is whether the tests also start the JetBrains Client frontend.
 * [contextPlugins] are the main modules of the plugins that the tests install into each IDE context, sorted.
 */
internal data class StarterTestTarget(
  @JvmField val product: String,
  @JvmField val frontend: Boolean,
  @JvmField val contextPlugins: List<String>,
)

/** The module graph that [resolveStarterTestTargets] reads. */
internal interface StarterTestModuleGraph {
  fun hasModule(name: String): Boolean

  fun isCommunity(name: String): Boolean

  /** The modules of the test runtime of [name] that it names itself, in the order of its dependencies. */
  fun directTestRuntime(name: String): List<String>

  /** Every module of the test runtime of [name], without [name] itself. */
  fun testRuntime(name: String): Set<String>
}

/** Reads [file], or returns `null` when it does not exist. A community checkout has no such file. */
internal fun readStarterBazelTests(file: Path): StarterBazelTests? {
  if (!Files.isRegularFile(file)) {
    return null
  }

  try {
    val root = Json.parseToJsonElement(Files.readString(file)).jsonObject
    val products = LinkedHashMap<String, String>()
    for ((module, product) in root.requireObject("products")) {
      products.put(module, product.jsonPrimitive.content)
    }
    val tests = LinkedHashMap<String, List<String>>()
    for ((module, plugins) in root.requireObject("tests")) {
      tests.put(module, plugins.jsonArray.map { it.jsonPrimitive.content })
    }
    return StarterBazelTests(products = products, tests = tests)
  }
  catch (e: IllegalArgumentException) {
    throw IllegalStateException("Cannot read $file: ${e.message}", e)
  }
}

private fun JsonObject.requireObject(key: String): JsonObject {
  return (get(key) ?: throw IllegalArgumentException("the file has no '$key'")).jsonObject
}

/**
 * Computes the `starter_test` of every row of [tests], by test module.
 *
 * The product comes from the nearest level of the test runtime that has a product module of [StarterBazelTests.products]:
 * the direct dependencies, then theirs, and so on. Two products on one level are an error, so a module states its
 * product by a direct dependency. A lambda module, one with the lambda test framework in its test runtime, also starts
 * the frontend. Its context plugins also hold the lambda test plugin and `<module>.plugin` when that module exists.
 *
 * A row fails for a module that the project does not have, for a community module, for a module without the dev build
 * server of IDE Starter in its test runtime, and for a lambda module with one plugin per mode. A context plugin that
 * the project does not have fails too. The message names [source].
 */
internal fun StarterBazelTests.resolveStarterTestTargets(graph: StarterTestModuleGraph, source: String): Map<String, StarterTestTarget> {
  val result = TreeMap<String, StarterTestTarget>()
  for ((module, plugins) in tests) {
    fun refuse(reason: String): Nothing = error("$source: the row '$module' $reason")

    if (!graph.hasModule(module)) refuse("names no module of the project")
    if (graph.isCommunity(module)) refuse("is a community module, which cannot load the ultimate `starter_test`")
    val testRuntime = graph.testRuntime(module)
    if (STARTER_BUILD_SERVER_MODULE !in testRuntime) refuse("has no $STARTER_BUILD_SERVER_MODULE in its test runtime")
    MODE_SPLIT_PLUGIN_SUFFIXES.map { "$module.$it" }.firstOrNull(graph::hasModule)?.let {
      refuse("has the mode plugin '$it'; a context plugin loads in every mode")
    }
    for (plugin in plugins) {
      if (!graph.hasModule(plugin)) refuse("names the context plugin '$plugin', which the project does not have")
    }

    val lambda = LAMBDA_TEST_FRAMEWORK_MODULE in testRuntime
    val contextPlugins = TreeSet(plugins)
    if (lambda) {
      contextPlugins.add(LAMBDA_TEST_PLUGIN_MODULE)
      "$module.plugin".takeIf(graph::hasModule)?.let(contextPlugins::add)
    }
    result.put(module, StarterTestTarget(
      product = nearestProduct(module, graph, testRuntime) ?: refuse("has no product module of 'products' in its test runtime"),
      frontend = lambda,
      contextPlugins = java.util.List.copyOf(contextPlugins),
    ))
  }
  return result
}

private fun StarterBazelTests.nearestProduct(module: String, graph: StarterTestModuleGraph, testRuntime: Set<String>): String? {
  val visited = HashSet<String>()
  visited.add(module)
  var level = listOf(module)
  while (level.isNotEmpty()) {
    level = level.flatMap(graph::directTestRuntime).filter { it in testRuntime && visited.add(it) }
    val found = level.filter { it in products }.associateWithTo(TreeMap()) { products.getValue(it) }
    if (found.values.toSet().size > 1) {
      error("The test runtime of '$module' has the product modules $found on one level; depend on one of them directly")
    }
    found.values.firstOrNull()?.let { return it }
  }
  return null
}

/**
 * The content of `build/starter-bazel-tests.generated.json`: the context plugins of each product, the union of
 * [StarterTestTarget.contextPlugins] of [targets], sorted. The dev distribution generator gives each of them a component.
 */
internal fun renderStarterBazelTestsGenerated(targets: Collection<StarterTestTarget>): String {
  val contextPlugins = TreeMap<String, TreeSet<String>>()
  for (target in targets) {
    contextPlugins.computeIfAbsent(target.product) { TreeSet() }.addAll(target.contextPlugins)
  }
  val json = buildJsonObject {
    put("generatedBy", "./build/jpsModelToBazel.cmd")
    putJsonObject("contextPlugins") {
      for ((product, modules) in contextPlugins) {
        putJsonArray(product) { modules.forEach { add(JsonPrimitive(it)) } }
      }
    }
  }
  return GENERATED_JSON.encodeToString(JsonElement.serializer(), json) + "\n"
}

private val GENERATED_JSON = Json { prettyPrint = true }

/** Writes `build/starter-bazel-tests.generated.json` under [ultimateRoot] when its content changes. */
internal fun writeStarterBazelTestsGenerated(ultimateRoot: Path, targets: Collection<StarterTestTarget>) {
  val file = ultimateRoot.resolve(STARTER_BAZEL_TESTS_GENERATED_RELATIVE_PATH)
  val content = renderStarterBazelTestsGenerated(targets)
  if (Files.isRegularFile(file) && Files.readString(file) == content) {
    return
  }
  Files.createDirectories(file.parent)
  Files.writeString(file, content)
}

/** The [StarterTestModuleGraph] of the modules of [moduleList], over the JPS test runtime classpath. */
internal class JpsStarterTestModuleGraph(private val moduleList: ModuleList) : StarterTestModuleGraph {
  override fun hasModule(name: String): Boolean = moduleList.hasModule(name)

  override fun isCommunity(name: String): Boolean = moduleList.getModuleDescriptor(name).isCommunity

  override fun directTestRuntime(name: String): List<String> = testRuntimeModules(name, recursively = false)

  override fun testRuntime(name: String): Set<String> = testRuntimeModules(name, recursively = true).toSet()

  private fun testRuntimeModules(name: String, recursively: Boolean): List<String> {
    val module: JpsModule = moduleList.getModuleDescriptor(name).module
    var enumerator = JpsJavaExtensionService.dependencies(module).withoutSdk()
    if (recursively) {
      enumerator = enumerator.recursively()
    }
    return enumerator.includedIn(JpsJavaClasspathKind.TEST_RUNTIME).modules.map { it.name }.filter { it != name }
  }
}
