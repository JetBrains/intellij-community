// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.fasterxml.jackson.jr.ob.JSON
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.Pair
import groovy.transform.CompileStatic
import org.jdom.Content
import org.jdom.Element
import org.jdom.JDOMException
import org.jdom.Namespace
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.ProductModulesLayout
import org.jetbrains.jps.model.module.JpsModule

import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Matcher

@CompileStatic
final class PluginsCollector {
  private PluginsCollector() {
  }

  static List<PluginLayout> collectCompatiblePluginsToPublish(Path providedModulesFile, @NotNull BuildContext context) {
    Map parse = JSON.std.mapFrom(Files.readString(providedModulesFile))
    Set<String> availableModulesAndPlugins = new HashSet<String>(parse.get("modules") as Collection)
    availableModulesAndPlugins.addAll(parse.get("plugins") as Collection)

    Map<String, PluginDescriptor> descriptorMap = collectPluginDescriptors(true, true, true, context)
    Map<String, PluginDescriptor> descriptorsMapWithBundled = collectPluginDescriptors(true, false, true, context)
    List<PluginLayout> result = new ArrayList(descriptorMap.size())
    for (PluginDescriptor descriptor : descriptorMap.values()) {
      if (isPluginCompatible(descriptor, availableModulesAndPlugins, descriptorsMapWithBundled)) {
        result.add(descriptor.pluginLayout)
      }
    }
    return result
  }

  private static boolean isPluginCompatible(@NotNull PluginDescriptor plugin,
                                            @NotNull Set<String> availableModulesAndPlugins,
                                            @NotNull Map<String, PluginDescriptor> nonCheckedModules) {
    nonCheckedModules.remove(plugin.id)
    for (declaredModule in plugin.declaredModules) {
      nonCheckedModules.remove(declaredModule)
    }
    for (requiredDependency in plugin.requiredDependencies) {
      if (availableModulesAndPlugins.contains(requiredDependency)) {
        continue
      }

      PluginDescriptor requiredPlugin = nonCheckedModules.get(requiredDependency)
      if (requiredPlugin != null && isPluginCompatible(requiredPlugin, availableModulesAndPlugins, nonCheckedModules)) {
        continue
      }
      return false
    }
    for (incompatiblePlugin in plugin.incompatiblePlugins) {
      if (availableModulesAndPlugins.contains(incompatiblePlugin)) {
        return false
      }
    }
    availableModulesAndPlugins.add(plugin.id)
    availableModulesAndPlugins.addAll(plugin.declaredModules)
    return true
  }

  @NotNull
  static Map<String, PluginDescriptor> collectPluginDescriptors(boolean skipImplementationDetailPlugins,
                                                                boolean skipBundledPlugins,
                                                                boolean honorCompatiblePluginsToIgnore,
                                                                BuildContext context) {
    Map<String, PluginDescriptor> pluginDescriptors = new LinkedHashMap<String, PluginDescriptor>()
    ProductModulesLayout productLayout = context.productProperties.productLayout
    Map<String, List<PluginLayout>> nonTrivialPlugins = productLayout.allNonTrivialPlugins.groupBy { it.mainModule }
    Set<String> allBundledPlugins = new HashSet<>(productLayout.bundledPluginModules)
    for (JpsModule jpsModule : context.project.modules) {
      String moduleName = jpsModule.name
      if ((skipBundledPlugins && allBundledPlugins.contains(moduleName)) ||
          (honorCompatiblePluginsToIgnore && productLayout.compatiblePluginsToIgnore.contains(moduleName))) {
        continue
      }

      // not a plugin
      if (moduleName == "intellij.idea.ultimate.resources" || moduleName == "intellij.lightEdit" || moduleName == "intellij.webstorm") {
        continue
      }

      Path pluginXml = context.findFileInModuleSources(moduleName, "META-INF/plugin.xml")
      if (pluginXml == null) {
        continue
      }

      PluginLayout pluginLayout = nonTrivialPlugins.get(moduleName)?.first()
      if (pluginLayout == null) {
        pluginLayout = PluginLayout.plugin(moduleName)
      }

      Element xml = JDOMUtil.load(pluginXml)
      if (JDOMUtil.isEmpty(xml)) {
        // throws an exception
        context.messages.error("Module '${moduleName}': '$pluginXml' is empty")
        continue
      }

      if (skipImplementationDetailPlugins && xml.getAttributeValue("implementation-detail") == "true") {
        context.messages.debug("PluginsCollector: skipping module '${moduleName}' since 'implementation-detail' == 'true' in '$pluginXml'")
        continue
      }

      JDOMXIncluder.resolveNonXIncludeElement(xml, pluginXml, new SourcesBasedXIncludeResolver(pluginLayout, context))

      String id = xml.getChildTextTrim("id") ?: xml.getChildTextTrim("name")
      if (id == null || id.isEmpty()) {
        // throws an exception
        context.messages.error("Module '${moduleName}': '$pluginXml' does not contain <id/> element")
        continue
      }

      Set<String> declaredModules = new HashSet<String>()
      for (moduleElement in xml.getChildren('module')) {
        String value = moduleElement.getAttributeValue('value')
        if (value) {
          declaredModules.add(value)
        }
      }

      Element content = xml.getChild("content")
      if (content) {
        for (module in content.getChildren("module")) {
          String name = module.getAttributeValue("name")
          if (name != null && !name.isEmpty()) {
            declaredModules.add(name)
          }
        }
      }

      def requiredDependencies = new HashSet<String>()
      def optionalDependencies = new ArrayList<Pair<String, String>>()
      for (dependency in xml.getChildren('depends')) {
        if (dependency.getAttributeValue('optional') != 'true') {
          requiredDependencies += dependency.getTextTrim()
        }
        else {
          optionalDependencies += new Pair(dependency.getTextTrim(), dependency.getAttributeValue("config-file"))
        }
      }
      def dependencies = xml.getChild('dependencies')
      if (dependencies != null) {
        for (plugin in dependencies.getChildren('plugin')) {
          def pluginId = plugin.getAttributeValue('id')
          if (pluginId) {
            requiredDependencies += pluginId
          }
        }
        for (module in dependencies.getChildren("module")) {
          String name = module.getAttributeValue("name")
          if (name != null && !name.isEmpty()) {
            requiredDependencies.add(name)
          }
        }
      }

      def incompatiblePlugins = new HashSet<String>()
      for (pluginId in xml.getChildren('incompatible-with')) {
        incompatiblePlugins += pluginId.getTextTrim()
      }

      String description = xml.getChildTextTrim("description")
      def pluginDescriptor = new PluginDescriptor(id, description, declaredModules, requiredDependencies, incompatiblePlugins,
                                                  optionalDependencies, pluginLayout)
      pluginDescriptors[id] = pluginDescriptor
      for (module in declaredModules) {
        pluginDescriptors[module] = pluginDescriptor
      }
    }
    return pluginDescriptors
  }

  static final class PluginDescriptor {
    final String id
    final String description
    final Set<String> declaredModules
    final Set<String> requiredDependencies
    final Set<String> incompatiblePlugins
    final List<Pair<String, String>> optionalDependencies
    final PluginLayout pluginLayout

    PluginDescriptor(String id,
                     String description,
                     Set<String> declaredModules,
                     Set<String> requiredDependencies,
                     Set<String> incompatiblePlugins,
                     List<Pair<String, String>> optionalDependencies,
                     PluginLayout pluginLayout) {
      this.id = id
      this.description = description
      this.declaredModules = declaredModules
      this.requiredDependencies = requiredDependencies
      this.incompatiblePlugins = incompatiblePlugins
      this.optionalDependencies = optionalDependencies
      this.pluginLayout = pluginLayout
    }
  }

  private static final class SourcesBasedXIncludeResolver implements JDOMXIncluder.PathResolver {
    private final PluginLayout pluginLayout
    private final BuildContext context

    SourcesBasedXIncludeResolver(@NotNull PluginLayout pluginLayout, @NotNull BuildContext context) {
      this.pluginLayout = pluginLayout
      this.context = context
    }

    @Override
    URL resolvePath(@NotNull String relativePath, @Nullable URL url) throws MalformedURLException {
      URL result = null
      for (moduleName in pluginLayout.includedModuleNames) {
        Path path = context.findFileInModuleSources(moduleName, relativePath)
        if (path != null) {
          result = path.toUri().toURL()
        }
      }
      if (result == null) {
        result = url == null ? new URL(relativePath) : new URL(url, relativePath)
      }
      if (result == null) {
        throw new IllegalArgumentException("Cannot resolve path $relativePath in ${pluginLayout.mainModule}")
      }
      return result
    }
  }

  @CompileStatic
  private static final class JDOMXIncluder {
    private static final Logger LOG = Logger.getInstance(JDOMXIncluder.class)

    /**
     * The original element will be mutated in place.
     */
    static void resolveNonXIncludeElement(@NotNull Element original,
                                          @NotNull Path base,
                                          @NotNull PathResolver pathResolver) throws MalformedURLException {
      LOG.assertTrue(!isIncludeElement(original))

      ArrayDeque<URL> bases = new ArrayDeque<>()
      bases.push(base.toUri().toURL())
      doResolveNonXIncludeElement(original, bases, pathResolver)
    }

    private static boolean isIncludeElement(Element element) {
      return element.getName() == "include" && element.getNamespace() == JDOMUtil.XINCLUDE_NAMESPACE
    }


    private static List<Element> resolveXIncludeElement(@NotNull Element element,
                                                        @NotNull Deque<URL> bases,
                                                        @NotNull PathResolver pathResolver) throws MalformedURLException {
      URL base = null
      if (!bases.isEmpty()) {
        base = bases.peek()
      }

      String href = element.getAttributeValue("href")
      assert href != null: "Missing href attribute"

      String baseAttribute = element.getAttributeValue("base", Namespace.XML_NAMESPACE)
      if (baseAttribute != null) {
        base = new URL(baseAttribute)
      }

      URL remote = pathResolver.resolvePath(href, base)
      assert !bases.contains(remote): "Circular XInclude Reference to " + remote.toExternalForm()

      final Element fallbackElement = element.getChild("fallback", element.getNamespace())
      List<Element> remoteParsed = parseRemote(bases, remote, fallbackElement, pathResolver)
      if (!remoteParsed.isEmpty()) {
        remoteParsed = extractNeededChildren(element, remoteParsed)
      }

      int i = 0
      for (; i < remoteParsed.size(); i++) {
        Element o = remoteParsed.get(i)
        if (isIncludeElement(o)) {
          List<Element> elements = resolveXIncludeElement(o, bases, pathResolver)
          remoteParsed.addAll(i, elements)
          i += elements.size() - 1
          remoteParsed.remove(i)
        }
        else {
          doResolveNonXIncludeElement(o, bases, pathResolver)
        }
      }

      for (Content content : remoteParsed) {
        content.detach()
      }
      return remoteParsed
    }

    @NotNull
    private static List<Element> extractNeededChildren(@NotNull Element element, @NotNull List<Element> remoteElements) {
      String xpointer = element.getAttributeValue("xpointer")
      if (xpointer == null) {
        xpointer = "xpointer(/idea-plugin/*)"
      }

      Matcher matcher = JDOMUtil.XPOINTER_PATTERN.matcher(xpointer)
      if (!matcher.matches()) {
        throw new RuntimeException("Unsupported XPointer: " + xpointer)
      }

      String pointer = matcher.group(1)
      matcher = JDOMUtil.CHILDREN_PATTERN.matcher(pointer)
      if (!matcher.matches()) {
        throw new RuntimeException("Unsupported pointer: " + pointer)
      }

      String rootTagName = matcher.group(1)

      assert remoteElements.size() == 1
      Element e = remoteElements.get(0)
      if (e.getName() != rootTagName) {
        return Collections.emptyList()
      }

      String subTagName = matcher.group(2)
      if (subTagName != null) {
        // cut off the slash
        e = e.getChild(subTagName.substring(1))
        assert e != null
      }
      return new ArrayList<>(e.getChildren())
    }

    private static List<Element> parseRemote(@NotNull Deque<URL> bases,
                                             @NotNull URL remote,
                                             @Nullable Element fallbackElement,
                                             @NotNull PathResolver pathResolver) {
      try {
        bases.push(remote)
        Element root = JDOMUtil.load(remote.openStream())
        List<Element> result
        if (isIncludeElement(root)) {
          result = resolveXIncludeElement(root, bases, pathResolver)
        }
        else {
          doResolveNonXIncludeElement(root, bases, pathResolver)
          result = Collections.singletonList(root)
        }
        return result
      }
      catch (JDOMException e) {
        throw new RuntimeException(e)
      }
      catch (IOException e) {
        if (fallbackElement != null) {
          return Collections.emptyList()
        }
        LOG.info(remote.toExternalForm() + " include ignored: " + e.getMessage())
        return Collections.emptyList()
      }
      finally {
        bases.pop()
      }
    }

    private static void doResolveNonXIncludeElement(@NotNull Element original,
                                                    @NotNull Deque<URL> bases,
                                                    @NotNull PathResolver pathResolver) throws MalformedURLException {
      List<Content> contentList = original.getContent()
      for (int i = contentList.size() - 1; i >= 0; i--) {
        Content content = contentList.get(i)
        if (content instanceof Element) {
          Element element = (Element)content
          if (isIncludeElement(element)) {
            original.setContent(i, resolveXIncludeElement(element, bases, pathResolver))
          }
          else {
            // process child element to resolve possible includes
            doResolveNonXIncludeElement(element, bases, pathResolver)
          }
        }
      }
    }

    interface PathResolver {
      @NotNull
      URL resolvePath(@NotNull String relativePath, @Nullable URL base) throws MalformedURLException;
    }
  }
}