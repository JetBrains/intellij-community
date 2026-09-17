// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "RedundantSuppression", "ReplaceGetOrSet", "ReplacePutWithAssignment")
package org.jetbrains.intellij.build.impl

import io.opentelemetry.api.trace.Span
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.DescriptorSearchPass
import org.jetbrains.intellij.build.JarPackagerDependencyHelper
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.PLATFORM_LOADER_JAR
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.UTIL_8_JAR
import org.jetbrains.intellij.build.UTIL_JAR
import org.jetbrains.intellij.build.UTIL_RT_JAR
import org.jetbrains.intellij.build.classPath.descriptorResolveContext
import org.jetbrains.intellij.build.classPath.getEmbeddedContentModulesOfPluginsWithUseIdeaClassloader
import org.jetbrains.intellij.build.forEachConcurrent
import org.jetbrains.intellij.build.withRestarter
import org.jetbrains.intellij.build.impl.PlatformJarNames.TEST_FRAMEWORK_JAR
import org.jetbrains.intellij.build.productLayout.ProductModulesLayout
import org.jetbrains.intellij.build.readDescriptor

/** Ordered module members of the fixed bootstrap and external-process jars. */
@ApiStatus.Internal
val PLATFORM_FIXED_JAR_MODULES: Map<String, List<String>> = mapOf(
  UTIL_RT_JAR to listOf("intellij.platform.util.rt"),
  // trove is not used by JB Client - fix RuntimeModuleRepositoryChecker assert
  "trove.jar" to listOf("intellij.platform.util.trove"),
  // maven uses JDOM in an external process
  UTIL_8_JAR to listOf(
    "intellij.platform.util.jdom",
    "intellij.platform.util.xmlDom",
    "intellij.platform.tracing.rt",
    "intellij.platform.util.base",
    "intellij.platform.util.base.multiplatform",
    "intellij.platform.diagnostic",
    // it contains common telemetry-related code (utils, TelemetryContext) for OpenTelemetry
    "intellij.platform.diagnostic.telemetry.rt",
    "intellij.platform.util",
    "intellij.platform.util.multiplatform",
    // it has package `kotlin.coroutines.jvm.internal` - should be packed into the same JAR as coroutine lib,
    // to ensure that package index will not report one more JAR in a search path
    "intellij.platform.bootstrap.coroutine",
    "intellij.platform.eel",  // EelFiles, which is a replacement for java.nio.file.Files, may be used everywhere
    "intellij.platform.eel.nioFs",  // NIO bridge for EEL (EelPath <-> Path conversions, EelPathBoundDescriptor)
  ),
  // platform-loader.jar is loaded by JVM classloader as part of loading our custom PathClassLoader class - reduce file size
  PLATFORM_LOADER_JAR to listOf(
    "intellij.platform.util.rt.java8",
    "intellij.platform.util.classLoader",
    "intellij.platform.util.zip",
    "intellij.platform.boot",
    "intellij.platform.runtime.repository",
    "intellij.platform.runtime.loader",
  ),
  UTIL_JAR to listOf(
    // Scala uses GeneralCommandLine in JPS plugin
    "intellij.platform.ide.util.io",
    "intellij.platform.extensions",
    "intellij.platform.util.nanoxml",
  ),
  "externalProcess-rt.jar" to listOf("intellij.platform.externalProcessAuthHelper.rt"),
  "forms_rt.jar" to listOf("intellij.java.guiForms.rt"),
  "jps-model.jar" to listOf(
    "intellij.platform.jps.model",
    "intellij.platform.jps.model.serialization",
    "intellij.platform.jps.model.impl",
  ),
  "external-system-rt.jar" to listOf("intellij.platform.externalSystem.rt", "intellij.platform.objectSerializer.annotations"),
)

private fun addModule(relativeJarPath: String, productLayout: ProductModulesLayout, layout: PlatformLayout) {
  layout.withModules(
    PLATFORM_FIXED_JAR_MODULES.getValue(relativeJarPath).asSequence()
      .filter { !productLayout.excludedModuleNames.contains(it) }
      .map { ModuleItem(moduleName = it, relativeOutputFile = relativeJarPath, reason = "addModule") }
  )
}

fun createPlatformLayout(context: BuildContext): PlatformLayout {
  return createPlatformLayout(
    productProperties = context.productProperties,
    outputProvider = context.outputProvider,
    validateImplicitPlatformModule = context.options.validateImplicitPlatformModule,
    useModularLoader = context.useModularLoader,
    isEmbeddedFrontendEnabled = context.isEmbeddedFrontendEnabled,
    runtimeDependencyResolver = RuntimeDependencyIndex((context as BuildContextImpl).jarPackagerDependencyHelper),
    bundledPluginModules = context.getBundledPluginModules(),
    sourceOnly = false,
    embedContentModuleDescriptors = context.options.embedProductContentModuleDescriptors,
    markModuleForScrambling = { moduleName, isEmbedded ->
      markContentModuleToScrambleIfNeeded(moduleName, context, isEmbedded)
    },
  )
}

/**
 * Derives the platform layout from product declarations and source descriptors. This does not execute patches or read compiled output.
 */
fun createPlatformLayout(
  productProperties: ProductProperties,
  outputProvider: ModuleOutputProvider,
  validateImplicitPlatformModule: Boolean = false,
  useModularLoader: Boolean = false,
  isEmbeddedFrontendEnabled: Boolean = productProperties.embeddedFrontendRootModule != null,
  bundledPluginModules: List<String> = getBundledPluginModules(productProperties, outputProvider),
): PlatformLayout {
  return createPlatformLayout(
    productProperties = productProperties,
    outputProvider = outputProvider,
    validateImplicitPlatformModule = validateImplicitPlatformModule,
    useModularLoader = useModularLoader,
    isEmbeddedFrontendEnabled = isEmbeddedFrontendEnabled,
    runtimeDependencyResolver = RuntimeDependencyIndex(JarPackagerDependencyHelper(outputProvider)),
    bundledPluginModules = bundledPluginModules,
    sourceOnly = true,
    embedContentModuleDescriptors = false,
    markModuleForScrambling = { _, _ -> false },
  )
}

private fun createPlatformLayout(
  productProperties: ProductProperties,
  outputProvider: ModuleOutputProvider,
  validateImplicitPlatformModule: Boolean,
  useModularLoader: Boolean,
  isEmbeddedFrontendEnabled: Boolean,
  runtimeDependencyResolver: RuntimeDependencyResolver,
  bundledPluginModules: List<String>,
  sourceOnly: Boolean,
  embedContentModuleDescriptors: Boolean,
  markModuleForScrambling: (String, Boolean) -> Boolean,
): PlatformLayout {
  val productLayout = productProperties.productLayout
  val layout = PlatformLayout()
  layout.withRestarter()
  for (customizer in productLayout.platformLayoutSpec) {
    customizer(layout)
  }
  val contentModuleFilter = createContentModuleFilter(
    project = outputProvider.findRequiredModule(productProperties.applicationInfoModule).project,
    productProperties = productProperties,
    outputProvider = outputProvider,
    bundledPluginModules = { bundledPluginModules },
  )
  for ((module, patterns) in productLayout.moduleExcludes) {
    layout.excludeFromModule(module, patterns)
  }

  addModule(UTIL_RT_JAR, productLayout = productLayout, layout = layout)
  addModule("trove.jar", productLayout = productLayout, layout = layout)
  addModule(UTIL_8_JAR, productLayout = productLayout, layout = layout)

  // todo as content module (IJPL-252372)
  // see ClassPathUtil.getUtilClassPath and ArtifactRepositoryManager.getClassesFromDependencies -
  // these libraries are used by JPS and must be a part of util-8.jar
  layout.withProjectLibraries(sequenceOf(
    "Log4J",
    "kotlin-stdlib",
    "slf4j-api",
    "slf4j-jdk14",
  ), UTIL_8_JAR)

  // the library is put to a separate JAR due to IJPL-248572; todo: include it only for Linux: IJPL-249098
  layout.withProjectLibraries(sequenceOf("jetbrains.intellij.deps.java.atk.wrapper.linux"))

  // https://jetbrains.team/p/ij/reviews/67104/timeline
  // https://youtrack.jetbrains.com/issue/IDEA-179784
  // https://youtrack.jetbrains.com/issue/IDEA-205600
  layout.withProjectLibraries(sequenceOf(
    "jaxb-runtime",
    "jaxb-api",
  ))

  // the library is put to a separate JAR due to IJPL-248591; it would be better to get rid of it completely, see IJPL-749
  layout.withModuleLibrary(libraryName = "swingx", moduleName = "intellij.libraries.swingx")

  addModule(PLATFORM_LOADER_JAR, productLayout = productLayout, layout = layout)
  addModule(UTIL_JAR, productLayout = productLayout, layout = layout)
  addModule("externalProcess-rt.jar", productLayout = productLayout, layout = layout)
  for (moduleName in PLATFORM_FIXED_JAR_MODULES.getValue("forms_rt.jar")) {
    if (!productLayout.excludedModuleNames.contains(moduleName)) {
      layout.withModule(moduleName, "forms_rt.jar")
    }
  }
  addModule("jps-model.jar", productLayout = productLayout, layout = layout)
  addModule("external-system-rt.jar", productLayout = productLayout, layout = layout)

  val explicit = ArrayList<ModuleItem>()
  for (moduleName in productLayout.productImplementationModules) {
    if (productLayout.excludedModuleNames.contains(moduleName)) {
      continue
    }

    explicit.add(ModuleItem(moduleName = moduleName, relativeOutputFile = "$moduleName.jar", reason = "productImplementationModules"))
    markModuleForScrambling(moduleName, true)
  }
  val explicitModuleNames = explicit.map { it.moduleName }

  // we should filter out modules which are included in plugins with `use-idea-classloader`
  val pluginsContents = getPluginLayoutsByJpsModuleNames(bundledPluginModules, productLayout).flatMapTo(LinkedHashSet()) {
    getEmbeddedContentModulesOfPluginsWithUseIdeaClassloader(
      pluginMainModule = it.mainModule,
      cacheContainer = null,
      outputProvider = outputProvider,
      contentModuleFilter = contentModuleFilter,
      sourceOnly = sourceOnly,
    )
  }

  val productPluginContentModules = processAndGetProductPluginContentModules(
    layout = layout,
    descriptorCache = layout.descriptorCacheContainer.forPlatform(layout),
    includedPlatformModulesPartialList = computePartialListToResolveIncludesAndCollectProductModules(
      layout = layout,
      explicitModuleNames = explicitModuleNames,
      productLayout = productLayout,
      pluginsContents = pluginsContents,
      runtimeDependencyResolver = runtimeDependencyResolver,
    ),
    productProperties = productProperties,
    outputProvider = outputProvider,
    contentModuleFilter = contentModuleFilter,
    descriptorContext = descriptorResolveContext(outputProvider, productProperties.javaClass.simpleName, sourceOnly),
    embedContentModuleDescriptors = embedContentModuleDescriptors,
    markModuleForScrambling = markModuleForScrambling,
  ).toCollection(LinkedHashSet())

  val implicit = computeImplicitRequiredModules(
    explicit = explicitModuleNames,
    layout = layout,
    productPluginContentModules = productPluginContentModules.mapTo(HashSet()) { it.moduleName },
    productLayout = productLayout,
    pluginsContents = pluginsContents,
    runtimeDependencyResolver = runtimeDependencyResolver,
  )

  if (validateImplicitPlatformModule) {
    val implicitContentModuleAllowlist = productProperties.getProductContentDescriptor()?.allowedMissingDependencies?.mapTo(HashSet()) { it.value } ?: emptySet()
    implicit.forEachConcurrent { (name, chain) ->
      validateImplicitPlatformModule(
        name = name,
        chain = chain,
        outputProvider = outputProvider,
        allowedMissingDependencies = implicitContentModuleAllowlist,
        isClientBuild = useModularLoader,
        sourceOnly = sourceOnly,
      )
    }
  }

  val filteredExplicit = LinkedHashSet(explicit)
  for (item in productPluginContentModules) {
    val iterator = filteredExplicit.iterator()
    while (iterator.hasNext()) {
      if (item.moduleName == iterator.next().moduleName) {
        // todo - error instead of warn
        Span.current().addEvent("product module MUST NOT BE explicitly specified: ${item.moduleName}")
        iterator.remove()
      }
    }
  }

  layout.withModules(
    (filteredExplicit.asSequence() +
     productPluginContentModules +
     implicit.asSequence().map {
       ModuleItem(
         moduleName = it.first,
         relativeOutputFile = "${it.first}.jar",
         reason = "<- " + it.second.asReversed().joinToString(separator = " <- ")
       )
     })
      .sortedBy { it.moduleName },
  )

  val platformMainModule = "intellij.platform.starter"
  if (isEmbeddedFrontendEnabled && layout.includedModules.none { it.moduleName == platformMainModule }) {
    /* this module is used by JetBrains Client, but it isn't packed in commercial IDEs, so let's put it in a separate JAR which won't be
       loaded when the IDE is started in the regular mode */
    layout.withModule(platformMainModule, "ext/platform-main.jar")
  }

  productProperties.validateLayout(layout)
  return layout
}

private fun computePartialListToResolveIncludesAndCollectProductModules(
  layout: PlatformLayout,
  explicitModuleNames: Collection<String>,
  productLayout: ProductModulesLayout,
  pluginsContents: Set<String>,
  runtimeDependencyResolver: RuntimeDependencyResolver,
): Collection<String> {
  val result = LinkedHashSet<String>()
  layout.includedModules.mapTo(result) { it.moduleName }
  computeImplicitRequiredModules(
    explicit = explicitModuleNames,
    layout = layout,
    productPluginContentModules = emptySet(),
    productLayout = productLayout,
    pluginsContents = pluginsContents,
    runtimeDependencyResolver = runtimeDependencyResolver,
  ).mapTo(result) { it.first }
  result.addAll(explicitModuleNames)
  return result
}

fun getEnabledPluginModules(pluginsToPublish: Set<PluginLayout>, context: BuildContext): Set<String> {
  val result = LinkedHashSet<String>()
  result.addAll(context.getBundledPluginModules())
  pluginsToPublish.mapTo(result) { it.mainModule }
  return result
}

private fun computeImplicitRequiredModules(
  explicit: Collection<String>,
  layout: PlatformLayout,
  productPluginContentModules: Set<String>,
  productLayout: ProductModulesLayout,
  pluginsContents: Set<String>,
  runtimeDependencyResolver: RuntimeDependencyResolver,
): List<Pair<String, PersistentList<String>>> {
  return collectTransitiveRuntimeDependencies(
    roots = buildImplicitTraversalRoots(
      explicit = explicit,
      layout = layout,
      productLayout = productLayout,
      productPluginContentModules = productPluginContentModules,
    ),
    blockedOrSeen = buildImplicitTraversalBlockedSet(
      explicit = explicit,
      layout = layout,
      productLayout = productLayout,
      productPluginContentModules = productPluginContentModules,
    ),
    omitFromResult = pluginsContents,
    dependencyResolver = runtimeDependencyResolver,
  )
}

private fun buildImplicitTraversalRoots(
  explicit: Collection<String>,
  layout: PlatformLayout,
  productLayout: ProductModulesLayout,
  productPluginContentModules: Set<String>,
): List<Pair<String, PersistentList<String>>> {
  val rootChain = persistentListOf<String>()
  return layout.filteredIncludedModuleNames(excludedRelativeJarPath = TEST_FRAMEWORK_JAR, includeFromSubdirectories = false)
    .plus(explicit)
    .filter {
      !productLayout.excludedModuleNames.contains(it) &&
      !productPluginContentModules.contains(it) &&
      !it.startsWith("intellij.pycharm.") &&
      !it.startsWith("intellij.python.") &&
      !it.startsWith("intellij.codeServer.") &&
      !it.startsWith("intellij.clion.") &&
      !it.startsWith("intellij.cidr.") &&
      !it.startsWith("intellij.appcode.") &&
      it != "fleet.backend" &&
      it != "intellij.codeServer" &&
      it != "intellij.goland"
    }
    .distinct()
    .sorted()
    .map { it to rootChain }
    .toList()
}

private fun buildImplicitTraversalBlockedSet(
  explicit: Collection<String>,
  layout: PlatformLayout,
  productLayout: ProductModulesLayout,
  productPluginContentModules: Set<String>,
): HashSet<String> {
  val blockedOrSeen = HashSet<String>()
  layout.includedModules.mapTo(blockedOrSeen) { it.moduleName }
  blockedOrSeen.addAll(explicit)
  blockedOrSeen.addAll(productPluginContentModules)
  blockedOrSeen.addAll(productLayout.excludedModuleNames)
  blockedOrSeen.add("fleet.backend")
  // Module intellij.featuresTrainer contains, so it is a plugin, but plugin must be not included in a platform
  // (chain: [intellij.pycharm.community, intellij.python.featuresTrainer])
  blockedOrSeen.add("intellij.pycharm.community")
  blockedOrSeen.add("intellij.python.featuresTrainer")
  blockedOrSeen.add("intellij.pycharm.ds")
  blockedOrSeen.add("intellij.notebooks.visualization")
  return blockedOrSeen
}

private fun validateImplicitPlatformModule(
  name: String,
  chain: PersistentList<String>,
  outputProvider: ModuleOutputProvider,
  allowedMissingDependencies: Set<String>,
  isClientBuild: Boolean,
  sourceOnly: Boolean,
) {
  val jpsModule = outputProvider.findRequiredModule(name)
  fun readModuleDescriptor(path: String): ByteArray? {
    return if (sourceOnly) {
      readDescriptor(module = jpsModule, path = path, outputProvider = outputProvider, pass = DescriptorSearchPass.PRODUCTION_SOURCES)
    }
    else {
      outputProvider.readFileContentFromModuleOutput(jpsModule, path)
    }
  }

  val pluginXml = readModuleDescriptor("META-INF/plugin.xml")
  check(pluginXml == null) {
    "Module $name contains ${pluginXml.contentToString()}, so it is a plugin, but plugin must be not included in a platform (chain: $chain)"
  }

  if (readModuleDescriptor(contentModuleNameToDescriptorFileName(name)) == null) {
    return
  }
  else if (allowedMissingDependencies.contains(name) || chain.firstOrNull() == "intellij.tools.testsBootstrap") {
    Span.current().addEvent("Suppressing implicit content module validation for $name via allowMissingDependencies (chain: $chain)")
  }
  else if (isClientBuild) {
    // RustIdeBuildTest failed, disable assertion as it is not a production classloader / packaging for now
    Span.current().addEvent("Suppressing implicit content module validation for $name via allowMissingDependencies " +
                            "(chain: $chain) because it is a client build (non-production classloader)")
  }
  else {
    error("Module $name is a content module. Implicit platform auto-inclusion is prohibited; plugin model must be the only truth for packaging (chain: $chain)")
  }
}

internal object ModuleIncludeReasons {
  const val PRODUCT_MODULES: String = "productModule"
  const val PRODUCT_EMBEDDED_MODULES: String = "productEmbeddedModule"

  fun isProductModule(reason: String?): Boolean = reason == PRODUCT_MODULES || reason == PRODUCT_EMBEDDED_MODULES
}
