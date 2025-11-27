// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/**
 * Content Module Embedding: Scrambling-Aware Plugin Descriptor Processing
 *
 * This file implements scrambling-aware content module descriptor embedding for plugin builds.
 * It solves two critical issues with code obfuscation:
 *
 * 1. Scrambling doesn't modify class names in CDATA sections
 * 2. Files modified by scrambling weren't being respected in final descriptors
 *
 * The solution uses conditional embedding that occurs AFTER scrambling, ensuring that
 * scrambled class names are correctly embedded in plugin descriptors.
 *
 * For detailed architecture, diagrams, and implementation details, see:
 * [CONTENT_MODULE_EMBEDDING.md](../../CONTENT_MODULE_EMBEDDING.md)
 *
 * @see embedContentModules
 * @see resolveAndEmbedContentModuleDescriptor
 */
package org.jetbrains.intellij.build.classPath

import com.intellij.openapi.util.JDOMUtil
import org.jdom.CDATA
import org.jdom.Element
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.FrontendModuleFilter
import org.jetbrains.intellij.build.JarPackagerDependencyHelper
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.findFileInModuleDependencies
import org.jetbrains.intellij.build.findUnprocessedDescriptorContent
import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.jetbrains.intellij.build.impl.DescriptorCacheContainer
import org.jetbrains.intellij.build.impl.LayoutPatcher
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.ScopedCachedDescriptorContainer
import org.jetbrains.intellij.build.impl.XIncludeElementResolver
import org.jetbrains.intellij.build.impl.contentModuleNameToDescriptorFileName
import org.jetbrains.intellij.build.impl.resolveIncludes
import org.jetbrains.intellij.build.impl.toLoadPath
import java.io.IOException
import java.nio.file.Files

/**
 * Defines a search scope for resolving XInclude references in plugin descriptors.
 *
 * Associates a set of JPS module names with their cached descriptor container,
 * allowing the XInclude resolver to find descriptor files (like plugin.xml)
 * within the specified modules.
 *
 * @property modules JPS module names that define this search scope
 * @property descriptorCache The cache containing pre-processed descriptors for these modules
 * @property searchInDependencies Whether to search in module dependencies
 */
internal data class DescriptorSearchScope(
  @JvmField val modules: Collection<String>,
  @JvmField val descriptorCache: ScopedCachedDescriptorContainer,
  @JvmField val searchInDependencies: SearchMode = SearchMode.WITH_DEPENDENCIES,
) {
  enum class SearchMode {
    WITH_DEPENDENCIES,
    WITHOUT_DEPENDENCIES,
    PLUGIN_COLLECTOR,
  }
}

/**
 * Embeds content module descriptors as CDATA in a plugin's root descriptor.
 *
 * This function is called **only for plugins that have scrambling enabled** (i.e., `pathsToScramble.isEmpty() == false`).
 * It reads content module descriptors from the cache **after scrambling has occurred**, ensuring that
 * scrambled class names are correctly embedded in the final plugin descriptor.
 *
 * Process:
 * 1. Iterates through all `<content>/<module>` elements in the root descriptor
 * 2. For each module, resolves its descriptor from the post-scrambling cache
 * 3. Applies optional modifications (e.g., `separate-jar` attribute for plugin modules)
 * 4. Embeds the resolved descriptor as CDATA in the module element
 *
 * For non-scrambled plugins, content modules remain as xi:include references for runtime resolution.
 *
 * See [CONTENT_MODULE_EMBEDDING.md](../../CONTENT_MODULE_EMBEDDING.md) for architecture details.
 *
 * @param rootElement The root plugin descriptor element (e.g., plugin.xml)
 * @param pluginLayout The plugin layout configuration
 * @param pluginDescriptorContainer The scoped cache containing post-scrambling descriptors
 * @param xIncludeResolver The resolver for xi:include elements
 * @param context The build context
 */
internal fun embedContentModules(
  rootElement: Element,
  pluginLayout: PluginLayout,
  pluginDescriptorContainer: ScopedCachedDescriptorContainer,
  xIncludeResolver: XIncludeElementResolverImpl,
  context: BuildContext,
) {
  val frontendModuleFilter = context.getFrontendModuleFilter()
  val dependencyHelper = (context as BuildContextImpl).jarPackagerDependencyHelper
  for (contentElement in rootElement.getChildren("content")) {
    for (moduleElement in contentElement.getChildren("module")) {
      val moduleName = moduleElement.getAttributeValue("name") ?: continue
      embedContentModule(
        moduleElement = moduleElement,
        pluginDescriptorContainer = pluginDescriptorContainer,
        xIncludeResolver = xIncludeResolver,
        moduleName = moduleName,
        dependencyHelper = dependencyHelper,
        pluginLayout = pluginLayout,
        frontendModuleFilter = frontendModuleFilter,
        context = context
      )
    }
  }
}

fun deprecatedResolveDescriptor(
  spec: PluginLayout.PluginLayoutSpec,
  clientModuleName: String,
  relativePath: String,
  additionalSearchModules: Collection<String> = emptyList(),
) {
  val layoutPatcherIfNoScrambling: LayoutPatcher = { moduleOutputPatcher, platformLayout, context ->
    context.findFileInModuleSources(clientModuleName, relativePath)?.let { file ->
      val xml = JDOMUtil.load(file)

      val descriptorCacheContainer = DescriptorCacheContainer()
      val clientDescriptorCache = descriptorCacheContainer.forPlugin(context.paths.tempDir.resolve("temp-client-cache"))
      val platformDescriptorCache = descriptorCacheContainer.forPlatform(platformLayout)

      val xIncludeResolver = XIncludeElementResolverImpl(
        searchPath = listOf(
          DescriptorSearchScope(listOf(clientModuleName), clientDescriptorCache),
          DescriptorSearchScope(additionalSearchModules, clientDescriptorCache),
          DescriptorSearchScope(
            modules = platformLayout.includedModules.mapTo(LinkedHashSet()) { it.moduleName },
            descriptorCache = platformDescriptorCache
          ),
        ),
        context = context
      )

      resolveIncludes(element = xml, elementResolver = xIncludeResolver)

      for (contentElement in xml.getChildren("content")) {
        for (moduleElement in contentElement.getChildren("module")) {
          val moduleName = moduleElement.getAttributeValue("name") ?: continue
          embedContentModule(
            moduleElement = moduleElement,
            pluginDescriptorContainer = clientDescriptorCache,
            xIncludeResolver = xIncludeResolver,
            context = context,
            moduleName = moduleName,
            dependencyHelper = (context as BuildContextImpl).jarPackagerDependencyHelper,
            pluginLayout = PluginLayout.pluginAuto(clientModuleName) {},
            frontendModuleFilter = context.getFrontendModuleFilter()
          )
        }
      }

      moduleOutputPatcher.patchModuleOutput(moduleName = clientModuleName, path = relativePath, content = JDOMUtil.write(xml))
    }
  }

  spec.withDeprecatedPostProcessor(layoutPatcherIfNoScrambling) { zipFileName, data, pluginLayout, platformLayout, pluginCachedDescriptorContainer, context ->
    if (zipFileName != relativePath) {
      return@withDeprecatedPostProcessor null
    }

    val xml = JDOMUtil.load(data)

    val xIncludeResolver = XIncludeElementResolverImpl(
      searchPath = listOf(
        DescriptorSearchScope(listOf(clientModuleName), pluginCachedDescriptorContainer),
        DescriptorSearchScope(additionalSearchModules, pluginCachedDescriptorContainer),
        DescriptorSearchScope(
          modules = platformLayout.includedModules.mapTo(LinkedHashSet()) { it.moduleName },
          descriptorCache = platformLayout.descriptorCacheContainer.forPlatform(platformLayout)
        ),
      ),
      context = context
    )

    resolveIncludes(element = xml, elementResolver = xIncludeResolver)

    for (contentElement in xml.getChildren("content")) {
      for (moduleElement in contentElement.getChildren("module")) {
        val moduleName = moduleElement.getAttributeValue("name") ?: continue
        embedContentModule(
          moduleElement = moduleElement,
          pluginDescriptorContainer = pluginCachedDescriptorContainer,
          xIncludeResolver = xIncludeResolver,
          moduleName = moduleName,
          dependencyHelper = (context as BuildContextImpl).jarPackagerDependencyHelper,
          pluginLayout = pluginLayout,
          frontendModuleFilter = context.getFrontendModuleFilter(),
          context = context
        )
      }
    }

    JDOMUtil.write(xml).encodeToByteArray()
  }
}

internal fun embedContentModule(
  moduleElement: Element,
  pluginDescriptorContainer: ScopedCachedDescriptorContainer,
  xIncludeResolver: XIncludeElementResolverImpl,
  moduleName: String,
  dependencyHelper: JarPackagerDependencyHelper,
  pluginLayout: PluginLayout,
  frontendModuleFilter: FrontendModuleFilter,
  context: CompilationContext,
) {
  resolveAndEmbedContentModuleDescriptor(
    moduleElement = moduleElement,
    descriptorCache = pluginDescriptorContainer,
    xIncludeResolver = xIncludeResolver,
    context = context,
    descriptorModifier = { descriptor ->
      val jpsModuleName = moduleName.substringBeforeLast('/')
      if (jpsModuleName == moduleName &&
          dependencyHelper.isPluginModulePackedIntoSeparateJar(
            module = context.findRequiredModule(jpsModuleName.removeSuffix("._test")),
            layout = pluginLayout,
            frontendModuleFilter = frontendModuleFilter
          )) {
        descriptor.setAttribute("separate-jar", "true")
      }
    }
  )
}

/**
 * Resolves and loads a content module descriptor from cache or source.
 *
 * Cache strategy:
 * 1. Check `cachedDescriptorContainer` for already-processed descriptor
 * 2. If not cached, load from module sources and cache it
 * 3. Resolve xi:include elements
 * 4. Return processed Element
 *
 * Critical: This function reads from the cache **after** scrambling, ensuring scrambled class names are used.
 *
 * @param moduleName The name of the content module (e.g., "my.plugin.core")
 * @param descriptorCache The scoped cache containing descriptors
 * @param xIncludeResolver The resolver for xi:include elements
 * @param context The compilation context
 * @return The resolved descriptor element with xi:includes processed
 */
private fun resolveContentModuleDescriptor(
  moduleName: String,
  descriptorCache: ScopedCachedDescriptorContainer,
  xIncludeResolver: XIncludeElementResolver,
  context: ModuleOutputProvider,
): Element {
  val descriptorFilename = contentModuleNameToDescriptorFileName(moduleName)
  val data = descriptorCache.getCachedFileData(descriptorFilename)
  val element = if (data == null) {
    val jpsModuleName = moduleName.substringBeforeLast('/')
    val data = requireNotNull(findUnprocessedDescriptorContent(module = context.findRequiredModule(jpsModuleName), path = descriptorFilename, context = context)) {
      "Cannot find file $descriptorFilename in module $jpsModuleName"
    }
    descriptorCache.putIfAbsent(descriptorFilename, data)
    JDOMUtil.load(data)
  }
  else {
    JDOMUtil.load(data)
  }
  resolveIncludes(element, xIncludeResolver)
  return element
}

/**
 * Resolves a content module descriptor and embeds it as CDATA in the module element.
 *
 * This is a helper function that combines resolution and embedding into a single operation.
 * It's designed to simplify the common pattern of: extract moduleName → check empty content → resolve → embed.
 *
 * Key features:
 * - Early return if content is already embedded (idempotent)
 * - Resolves descriptor from cache (post-scrambling if applicable)
 * - Supports optional descriptor modifications via callback (e.g., adding attributes)
 * - Embeds result as CDATA, ensuring XML special characters are preserved
 *
 * Critical: This function reads from the cache **after** scrambling has been applied,
 * ensuring that scrambled class names are correctly embedded.
 *
 * See [CONTENT_MODULE_EMBEDDING.md](../../CONTENT_MODULE_EMBEDDING.md) for architecture details.
 *
 * @param moduleElement The `<module>` element to embed content into
 * @param descriptorCache The scoped cache containing post-scrambling descriptors
 * @param xIncludeResolver The resolver for xi:include elements
 * @param context The compilation context
 * @param descriptorModifier Optional callback to modify the resolved descriptor before embedding
 */
internal fun resolveAndEmbedContentModuleDescriptor(
  moduleElement: Element,
  descriptorCache: ScopedCachedDescriptorContainer,
  xIncludeResolver: XIncludeElementResolverImpl,
  context: CompilationContext,
  descriptorModifier: ((Element) -> Unit)? = null,
) {
  if (!moduleElement.content.isEmpty()) {
    return
  }

  val moduleName = moduleElement.getAttributeValue("name") ?: return
  val descriptor = resolveContentModuleDescriptor(
    moduleName = moduleName,
    descriptorCache = descriptorCache,
    xIncludeResolver = xIncludeResolver.copyWithExtraSearchPath(moduleName, descriptorCache),
    context = context,
  )

  descriptorModifier?.invoke(descriptor)
  moduleElement.setContent(CDATA(JDOMUtil.write(descriptor)))
}

internal class XIncludeElementResolverImpl(
  private val searchPath: List<DescriptorSearchScope>,
  private val context: BuildContext,
) : XIncludeElementResolver {
  fun copyWithExtraSearchPath(moduleName: String, container: ScopedCachedDescriptorContainer): XIncludeElementResolverImpl {
    for (scope in searchPath) {
      if (scope.modules.contains(moduleName)) {
        // mostly all our products have incorrect layout (especially rider or clion), so, check only IDEA for now
        if (context.productProperties::class.java.simpleName == "org.jetbrains.intellij.build.IdeaUltimateProperties") {
          require(scope.descriptorCache == container) {
            "Module '$moduleName' is already in search path with a different descriptor cache container. " +
            "Expected the same container instance, but found a mismatch. This indicates an inconsistency in descriptor caching."
          }
        }
        return this
      }
    }
    return XIncludeElementResolverImpl(listOf(DescriptorSearchScope(
      modules = listOf(moduleName),
      descriptorCache = container,
      // extra search path is needed when we want to resolve xi:include references in the content module itself, we should not search in dependencies
      searchInDependencies = DescriptorSearchScope.SearchMode.WITHOUT_DEPENDENCIES,
    )) + searchPath, context)
  }

  override fun resolveElement(relativePath: String, isOptional: Boolean, isDynamic: Boolean): Element? {
    if (isOptional || isDynamic) {
      // It isn't safe to resolve includes at build time if they're optional.
      // This could lead to issues when running another product using this distribution.
      // E.g., if the corresponding module is somehow being excluded on runtime.
      return null
    }

    val loadPath = toLoadPath(relativePath)

    for (searchPath in searchPath) {
      val descriptorCache = searchPath.descriptorCache
      descriptorCache.getCachedFileData(loadPath)?.let {
        return JDOMUtil.load(it)
      }

      // resolve module set files directly from generated directories
      if (descriptorCache.isModuleSetOwner && loadPath.startsWith("META-INF/intellij.moduleSets.")) {
        for (provider in context.productProperties.moduleSetsProviders) {
          val file = provider.getOutputDirectory(context.paths).resolve(loadPath)
          val data = try {
            Files.readAllBytes(file)
          }
          catch (_: IOException) {
            continue
          }

          // if someone else has resolved this file before, use their result
          descriptorCache.putIfAbsent(loadPath, data)
          return JDOMUtil.load(data)
        }
      }

      for (module in searchPath.modules) {
        findUnprocessedDescriptorContent(context.findRequiredModule(module), loadPath, context)?.let { data ->
          descriptorCache.putIfAbsent(loadPath, data)
          return JDOMUtil.load(data)
        }
      }

      val searchInDependencies = searchPath.searchInDependencies
      // search in module deps only if we cannot find in modules
      if (searchInDependencies != DescriptorSearchScope.SearchMode.WITHOUT_DEPENDENCIES) {
        val processedModules = HashSet(searchPath.modules)
        for (module in searchPath.modules) {
          findFileInModuleDependencies(
            module = context.findRequiredModule(module),
            relativePath = loadPath,
            context = context,
            processedModules = processedModules,
            recursiveModuleExclude = if (searchInDependencies == DescriptorSearchScope.SearchMode.PLUGIN_COLLECTOR) "intellij.platform." else null,
          )?.let { data ->
            descriptorCache.putIfAbsent(loadPath, data)
            return JDOMUtil.load(data)
          }
        }
      }
    }

    if (searchPath.singleOrNull()?.searchInDependencies == DescriptorSearchScope.SearchMode.PLUGIN_COLLECTOR) {
      if (badIncludesForPluginCollector.contains(loadPath)) {
        return null
      }

      //  run CodeServerBuildTest
      if (loadPath.startsWith("META-INF/bdide-")) {
        return null
      }
      val requestor = searchPath.singleOrNull()?.modules?.singleOrNull()
      if (requestor != null && (requestor.startsWith("intellij.android.") ||
                                requestor == "intellij.rustrover.plugin" ||
                                requestor == "intellij.javascript.plugin")) {
        return null
      }
    }
    throw IllegalStateException("Cannot resolve '$loadPath' in $searchPath")
  }
}

private val badIncludesForPluginCollector = hashSetOf(
  // rider includes some CWM files
  "META-INF/designer-gradle.xml",
  "META-INF/cwmBackendConnection.xml",
  // android module does not have dependency at all in iml
  "META-INF/screenshot-testing-gradle.xml",
  "META-INF/screenshot-testing.xml",
  "META-INF/server-flags.xml",
  // intellij.bigdatatools.core doesn't depend on intellij.bigdatatools.aws
  "META-INF/bigdataide-aws.xml",
  "META-INF/app-servers-service-view-integration.xml",
  "META-INF/js-plugin.xml",
)