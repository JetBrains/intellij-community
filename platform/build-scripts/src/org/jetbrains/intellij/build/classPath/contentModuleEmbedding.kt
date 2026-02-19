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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jdom.CDATA
import org.jdom.Element
import org.jdom.Namespace
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.FrontendModuleFilter
import org.jetbrains.intellij.build.JarPackagerDependencyHelper
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.findFileInModuleLibraryDependencies
import org.jetbrains.intellij.build.findUnprocessedDescriptorContent
import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.jetbrains.intellij.build.impl.DescriptorCacheContainer
import org.jetbrains.intellij.build.impl.LayoutPatcher
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.ScopedCachedDescriptorContainer
import org.jetbrains.intellij.build.impl.contentModuleNameToDescriptorFileName
import org.jetbrains.intellij.build.impl.toLoadPath
import org.jetbrains.jps.model.module.JpsModuleDependency

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
internal suspend fun embedContentModules(
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
        outputProvider = context.outputProvider,
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

      @Suppress("RAW_RUN_BLOCKING")
      runBlocking(Dispatchers.IO) {
        resolveIncludes(element = xml, elementResolver = xIncludeResolver)

        for (contentElement in xml.getChildren("content")) {
          for (moduleElement in contentElement.getChildren("module")) {
            val moduleName = moduleElement.getAttributeValue("name") ?: continue
            embedContentModule(
              moduleElement = moduleElement,
              pluginDescriptorContainer = clientDescriptorCache,
              xIncludeResolver = xIncludeResolver,
              moduleName = moduleName,
              dependencyHelper = (context as BuildContextImpl).jarPackagerDependencyHelper,
              pluginLayout = PluginLayout.pluginAuto(clientModuleName) {},
              frontendModuleFilter = context.getFrontendModuleFilter(),
              outputProvider = context.outputProvider,
            )
          }
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

    @Suppress("RAW_RUN_BLOCKING")
    return@withDeprecatedPostProcessor runBlocking(Dispatchers.IO) {
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
            outputProvider = context.outputProvider,
          )
        }
      }

      JDOMUtil.write(xml).encodeToByteArray()
    }
  }
}

internal suspend fun embedContentModule(
  moduleElement: Element,
  pluginDescriptorContainer: ScopedCachedDescriptorContainer,
  xIncludeResolver: XIncludeElementResolverImpl,
  moduleName: String,
  dependencyHelper: JarPackagerDependencyHelper,
  pluginLayout: PluginLayout,
  frontendModuleFilter: FrontendModuleFilter,
  outputProvider: ModuleOutputProvider,
) {
  resolveAndEmbedContentModuleDescriptor(
    moduleElement = moduleElement,
    descriptorCache = pluginDescriptorContainer,
    xIncludeResolver = xIncludeResolver,
    outputProvider = outputProvider,
    descriptorModifier = { descriptor ->
      val jpsModuleName = moduleName.substringBeforeLast('/')
      if (jpsModuleName == moduleName &&
          dependencyHelper.isPluginModulePackedIntoSeparateJar(
            module = outputProvider.findRequiredModule(jpsModuleName.removeSuffix("._test")),
            layout = pluginLayout,
            frontendModuleFilter = frontendModuleFilter,
          )) {
        descriptor.setAttribute("separate-jar", "true")
      }
    }
  )
}

private suspend fun resolveContentModuleDescriptor(
  moduleName: String,
  descriptorCache: ScopedCachedDescriptorContainer,
  xIncludeResolver: XIncludeElementResolverImpl,
  outputProvider: ModuleOutputProvider,
): Element {
  val descriptorFilename = contentModuleNameToDescriptorFileName(moduleName)
  val data = descriptorCache.getCachedFileData(descriptorFilename)
  val element = if (data == null) {
    val jpsModuleName = moduleName.substringBeforeLast('/')
    val data = requireNotNull(
      findUnprocessedDescriptorContent(
        module = outputProvider.findRequiredModule(jpsModuleName),
        path = descriptorFilename,
        outputProvider = outputProvider,
      )
    ) {
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

internal suspend fun resolveAndEmbedContentModuleDescriptor(
  moduleElement: Element,
  descriptorCache: ScopedCachedDescriptorContainer,
  xIncludeResolver: XIncludeElementResolverImpl,
  outputProvider: ModuleOutputProvider,
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
    outputProvider = outputProvider,
  )

  descriptorModifier?.invoke(descriptor)
  moduleElement.setContent(CDATA(JDOMUtil.write(descriptor)))
}

internal class XIncludeElementResolverImpl(
  private val searchPath: List<DescriptorSearchScope>,
  private val context: BuildContext,
) {
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

  suspend fun resolveElement(relativePath: String, isOptional: Boolean, isDynamic: Boolean): Element? {
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

      val outputProvider = context.outputProvider
      for (module in searchPath.modules) {
        findUnprocessedDescriptorContent(
          outputProvider.findRequiredModule(module),
          loadPath,
          outputProvider,
        )?.let { data ->
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
            module = outputProvider.findRequiredModule(module),
            relativePath = loadPath,
            outputProvider = outputProvider,
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
      val requestor = searchPath.singleOrNull()?.modules?.singleOrNull()
      if (shouldSkipPluginCollectorInclude(loadPath, requestor)) {
        return null
      }
    }
    throw IllegalStateException("Cannot resolve '$loadPath' in $searchPath")
  }
}

private fun shouldSkipPluginCollectorInclude(loadPath: String, requestor: String?): Boolean {
  if (badIncludesForPluginCollector.contains(loadPath)) {
    return true
  }

  //  run CodeServerBuildTest
  if (loadPath.startsWith("META-INF/bdide-")) {
    return true
  }
  return requestor != null && (requestor.startsWith("intellij.android.") ||
                               requestor == "intellij.rustrover.plugin" ||
                               requestor == "intellij.javascript.plugin")
}

private fun isIncludeElementFor(element: Element): Boolean {
  return element.name == "include" && element.namespace == JDOMUtil.XINCLUDE_NAMESPACE
}

internal suspend fun resolveIncludes(element: Element, elementResolver: XIncludeElementResolverImpl) {
  check(!isIncludeElementFor(element))
  doResolveNonXIncludeElementFromCache(original = element, elementResolver = elementResolver)
}

@Suppress("DuplicatedCode")
private suspend fun resolveXIncludeElement(
  element: Element,
  elementResolver: XIncludeElementResolverImpl,
): MutableList<Element>? {
  val href = requireNotNull(element.getAttributeValue("href")) { "Missing href attribute" }

  val baseAttribute = element.getAttributeValue("base", Namespace.XML_NAMESPACE)
  if (baseAttribute != null) {
    throw UnsupportedOperationException("`base` attribute is not supported")
  }

  val fallbackElement = element.getChild("fallback", element.namespace)
  val isDynamic = element.getAttribute("includeUnless") != null || element.getAttribute("includeIf") != null
  val remoteElement = elementResolver.resolveElement(
    relativePath = href,
    isOptional = fallbackElement != null,
    isDynamic = isDynamic,
  ) ?: return null

  val remoteParsed = extractNeededChildrenFor(element, remoteElement)

  // Process all children, recursively resolving any nested xi:include elements
  var i = 0
  while (i < remoteParsed.size) {
    val child = remoteParsed[i]
    if (isIncludeElementFor(child)) {
      val elements = resolveXIncludeElement(element = child, elementResolver = elementResolver)
      if (elements != null) {
        if (elements.isEmpty()) {
          // Remove the xi:include element that resolves to nothing
          remoteParsed.removeAt(i)
          i--  // Adjust index since we removed an element
        }
        else {
          // Replace the xi:include element with resolved elements
          remoteParsed.removeAt(i)
          remoteParsed.addAll(i, elements)
          // Skip over the newly inserted elements (loop will increment i by 1)
          i += elements.size - 1
        }
      }
    }
    else {
      doResolveNonXIncludeElementFromCache(original = child, elementResolver = elementResolver)
    }

    i++
  }

  for (elementToDetach in remoteParsed) {
    elementToDetach.detach()
  }
  return remoteParsed
}

private suspend fun doResolveNonXIncludeElementFromCache(original: Element, elementResolver: XIncludeElementResolverImpl) {
  val contentList = original.content
  for (i in contentList.size - 1 downTo 0) {
    val content = contentList[i]
    if (content is Element) {
      if (isIncludeElementFor(content)) {
        val result = resolveXIncludeElement(element = content, elementResolver = elementResolver)
        if (result != null) {
          original.setContent(i, result)
        }
      }
      else {
        // process child element to resolve possible includes
        doResolveNonXIncludeElementFromCache(original = content, elementResolver = elementResolver)
      }
    }
  }
}

@Suppress("DuplicatedCode")
private fun extractNeededChildrenFor(element: Element, remoteElement: Element): MutableList<Element> {
  val xpointer = element.getAttributeValue("xpointer") ?: "xpointer(/idea-plugin/*)"

  var matcher = JDOMUtil.XPOINTER_PATTERN.matcher(xpointer)
  if (!matcher.matches()) {
    throw RuntimeException("Unsupported XPointer: $xpointer")
  }

  val pointer = matcher.group(1)
  matcher = JDOMUtil.CHILDREN_PATTERN.matcher(pointer)
  if (!matcher.matches()) {
    throw RuntimeException("Unsupported pointer: $pointer")
  }

  val rootTagName = matcher.group(1)

  var e = remoteElement
  if (e.name != rootTagName) {
    return mutableListOf()
  }

  val subTagName = matcher.group(2)
  if (subTagName != null) {
    // cut off the slash
    e = requireNotNull(e.getChild(subTagName.substring(1))) { "Child element not found: ${subTagName.substring(1)}" }
  }
  return e.children.toMutableList()
}

private suspend fun findFileInModuleDependencies(
  module: org.jetbrains.jps.model.module.JpsModule,
  relativePath: String,
  outputProvider: ModuleOutputProvider,
  processedModules: MutableSet<String>,
  recursiveModuleExclude: String? = null,
): ByteArray? {
  findFileInModuleLibraryDependencies(module, relativePath, outputProvider)?.let {
    return it
  }

  return findFileInModuleDependenciesRecursive(
    module = module,
    relativePath = relativePath,
    provider = outputProvider,
    processedModules = processedModules,
    recursiveModuleExclude = recursiveModuleExclude,
  )
}

private suspend fun findFileInModuleDependenciesRecursive(
  module: org.jetbrains.jps.model.module.JpsModule,
  relativePath: String,
  provider: ModuleOutputProvider,
  processedModules: MutableSet<String>,
  recursiveModuleExclude: String? = null,
): ByteArray? {
  for (dependency in module.dependenciesList.dependencies) {
    if (dependency !is JpsModuleDependency) {
      continue
    }

    val moduleName = dependency.moduleReference.moduleName
    if (!processedModules.add(moduleName)) {
      continue
    }

    val dependentModule = provider.findRequiredModule(moduleName)
    findUnprocessedDescriptorContent(module = dependentModule, path = relativePath, outputProvider = provider)?.let {
      return it
    }

    // if recursiveModuleFilter is null, it means that non-direct search not needed
    if (recursiveModuleExclude != null && !moduleName.startsWith(recursiveModuleExclude)) {
      findFileInModuleDependenciesRecursive(
        module = dependentModule,
        relativePath = relativePath,
        provider = provider,
        processedModules = processedModules,
        recursiveModuleExclude = recursiveModuleExclude,
      )?.let {
        return it
      }
    }
  }
  return null
}

private val badIncludesForPluginCollector = hashSetOf(
  // rider includes some CWM files
  "META-INF/designer-gradle.xml",
  // android module does not have dependency at all in iml
  "META-INF/screenshot-testing-gradle.xml",
  "META-INF/screenshot-testing.xml",
  "META-INF/server-flags.xml",
  // intellij.bigdatatools.core doesn't depend on intellij.bigdatatools.aws
  "META-INF/bigdataide-aws.xml",
  "META-INF/app-servers-service-view-integration.xml",
  "META-INF/js-plugin.xml",
)
