// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.components.ServiceDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.extensions.impl.BeanExtensionPoint;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.extensions.impl.InterfaceExtensionPoint;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.Interner;
import com.intellij.util.messages.ListenerDescriptor;
import org.jdom.Attribute;
import org.jdom.Content;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

final class XmlReader {
  static final String APPLICATION_SERVICE = "com.intellij.applicationService";
  static final String PROJECT_SERVICE = "com.intellij.projectService";
  static final String MODULE_SERVICE = "com.intellij.moduleService";
  private static final String ATTRIBUTE_AREA = "area";

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  static boolean isSuitableForOs(@NotNull String os) {
    if (os.isEmpty()) {
      return true;
    }

    if (os.equals(IdeaPluginDescriptorImpl.OS.mac.name())) {
      return SystemInfoRt.isMac;
    }
    else if (os.equals(IdeaPluginDescriptorImpl.OS.linux.name())) {
      return SystemInfoRt.isLinux;
    }
    else if (os.equals(IdeaPluginDescriptorImpl.OS.windows.name())) {
      return SystemInfoRt.isWindows;
    }
    else if (os.equals(IdeaPluginDescriptorImpl.OS.unix.name())) {
      return SystemInfoRt.isUnix;
    }
    else if (os.equals(IdeaPluginDescriptorImpl.OS.freebsd.name())) {
      return SystemInfoRt.isFreeBSD;
    }
    else {
      throw new IllegalArgumentException("Unknown OS '" + os + "'");
    }
  }

  private static @NotNull ServiceDescriptor readServiceDescriptor(@NotNull Element element,
                                                                  @NotNull DescriptorListLoadingContext loadingContext) {
    ServiceDescriptor descriptor = new ServiceDescriptor();
    descriptor.serviceInterface = element.getAttributeValue("serviceInterface");
    descriptor.serviceImplementation = StringUtil.nullize(element.getAttributeValue("serviceImplementation"));
    descriptor.testServiceImplementation = StringUtil.nullize(element.getAttributeValue("testServiceImplementation"));
    descriptor.headlessImplementation = StringUtil.nullize(element.getAttributeValue("headlessImplementation"));
    descriptor.configurationSchemaKey = element.getAttributeValue("configurationSchemaKey");

    String preload = element.getAttributeValue("preload");
    if (preload != null) {
      switch (preload) {
        case "true":
          descriptor.preload = ServiceDescriptor.PreloadMode.TRUE;
          break;
        case "await":
          descriptor.preload = ServiceDescriptor.PreloadMode.AWAIT;
          break;
        case "notHeadless":
          descriptor.preload = ServiceDescriptor.PreloadMode.NOT_HEADLESS;
          break;
        case "notLightEdit":
          descriptor.preload = ServiceDescriptor.PreloadMode.NOT_LIGHT_EDIT;
          break;
        default:
          loadingContext.getLogger().error("Unknown preload mode value: " + JDOMUtil.writeElement(element));
          break;
      }
    }

    descriptor.overrides = Boolean.parseBoolean(element.getAttributeValue("overrides"));
    return descriptor;
  }

  static void readListeners(@NotNull IdeaPluginDescriptorImpl descriptor, @NotNull Element list, @NotNull ContainerDescriptor containerDescriptor) {
    List<Content> content = list.getContent();
    List<ListenerDescriptor> result = containerDescriptor.listeners;
    if (result == null) {
      result = new ArrayList<>(content.size());
      containerDescriptor.listeners = result;
    }
    else {
      ((ArrayList<ListenerDescriptor>)result).ensureCapacity(result.size() + content.size());
    }

    for (Content item : content) {
      if (!(item instanceof Element)) {
        continue;
      }

      Element child = (Element)item;

      String os = child.getAttributeValue("os");
      if (os != null && !isSuitableForOs(os)) {
        continue;
      }

      String listenerClassName = child.getAttributeValue("class");
      String topicClassName = child.getAttributeValue("topic");
      if (listenerClassName == null || topicClassName == null) {
        PluginManagerCore.getLogger().error("Listener descriptor is not correct: " + JDOMUtil.writeElement(child));
      }
      else {
        result.add(new ListenerDescriptor(listenerClassName, topicClassName,
                                          getBoolean("activeInTestMode", child), getBoolean("activeInHeadlessMode", child), descriptor));
      }
    }
  }

  static void readIdAndName(@NotNull IdeaPluginDescriptorImpl descriptor, @NotNull Element element) {
    String idString = descriptor.myId == null ? element.getChildTextTrim("id") : descriptor.myId.getIdString();
    String name = element.getChildTextTrim("name");
    if (idString == null) {
      idString = name;
    }
    else if (name == null) {
      name = idString;
    }

    descriptor.myName = name;
    if (descriptor.myId == null) {
      descriptor.myId = idString == null || idString.isEmpty() ? null : PluginId.getId(idString);
    }
  }

  static void readMetaInfo(@NotNull IdeaPluginDescriptorImpl descriptor, @NotNull Element element) {
    if (!element.hasAttributes()) {
      return;
    }

    List<Attribute> attributes = element.getAttributes();
    for (Attribute attribute : attributes) {
      switch (attribute.getName()) {
        case "url":
          descriptor.myUrl = StringUtil.nullize(attribute.getValue());
          break;

        case "use-idea-classloader":
          descriptor.myUseIdeaClassLoader = Boolean.parseBoolean(attribute.getValue());
          break;

        case "allow-bundled-update":
          descriptor.myAllowBundledUpdate = Boolean.parseBoolean(attribute.getValue());
          break;

        case "implementation-detail":
          descriptor.myImplementationDetail = Boolean.parseBoolean(attribute.getValue());
          break;

        case "version":
          String internalVersionString = StringUtil.nullize(attribute.getValue());
          if (internalVersionString != null) {
            try {
              Integer.parseInt(internalVersionString);
            }
            catch (NumberFormatException e) {
              PluginManagerCore.getLogger().error(new PluginException("Invalid value in plugin.xml format version: '" + internalVersionString + "'", e, descriptor.myId));
            }
          }
          break;
      }
    }
  }

  static <T> void readDependencies(@NotNull IdeaPluginDescriptorImpl rootDescriptor,
                                   @NotNull IdeaPluginDescriptorImpl descriptor,
                                   @NotNull DescriptorLoadingContext context,
                                   @NotNull PathBasedJdomXIncluder.PathResolver<T> pathResolver,
                                   @NotNull List<PluginDependency> dependencies) {
    List<String> visitedFiles = null;
    for (PluginDependency dependency : dependencies) {
      if (dependency.isDisabledOrBroken) {
        continue;
      }

      // because of https://youtrack.jetbrains.com/issue/IDEA-206274, configFile maybe not only for optional dependencies
      String configFile = dependency.configFile;
      if (configFile == null) {
        continue;
      }

      if (pathResolver instanceof ClassPathXmlPathResolver &&
          context.parentContext.checkOptionalConfigShortName(configFile, descriptor, rootDescriptor)) {
        continue;
      }

      Element element;
      try {
        element = pathResolver.resolvePath(descriptor.basePath, configFile, context.parentContext.getXmlFactory());
      }
      catch (IOException | JDOMException e) {
        String message = "Plugin " + rootDescriptor + " misses optional descriptor " + configFile;
        if (context.parentContext.ignoreMissingSubDescriptor) {
          context.parentContext.getLogger().info(message, e);
        }
        else {
          throw new RuntimeException(message, e);
        }
        continue;
      }

      if (visitedFiles == null) {
        visitedFiles = context.parentContext.getVisitedFiles();
      }

      checkCycle(rootDescriptor, configFile, visitedFiles);

      // effective descriptor cannot be used because not yet clear, is dependency resolvable or not
      IdeaPluginDescriptorImpl subDescriptor = new IdeaPluginDescriptorImpl(descriptor.path, descriptor.basePath, false);
      visitedFiles.add(configFile);
      if (!subDescriptor.readExternal(element, pathResolver, context, rootDescriptor)) {
        subDescriptor = null;
      }
      visitedFiles.clear();

      if (subDescriptor != null) {
        dependency.subDescriptor = subDescriptor;
      }
    }
  }

  private static void checkCycle(@NotNull IdeaPluginDescriptorImpl rootDescriptor,
                                 @NotNull String configFile,
                                 @NotNull List<String> visitedFiles) {
    for (int i = 0, n = visitedFiles.size(); i < n; i++) {
      if (configFile.equals(visitedFiles.get(i))) {
        List<String> cycle = visitedFiles.subList(i, visitedFiles.size());
        throw new RuntimeException("Plugin " + rootDescriptor + " optional descriptors form a cycle: " + String.join(", ", cycle));
      }
    }
  }

  private static boolean getBoolean(@NotNull String name, @NotNull Element child) {
    String value = child.getAttributeValue(name);
    return value == null || Boolean.parseBoolean(value);
  }

  static @Nullable LinkedHashMap<String, List<Element>> readExtensions(@NotNull IdeaPluginDescriptorImpl descriptor,
                                                                       @Nullable LinkedHashMap<String, List<Element>> epNameToExtensions,
                                                                       @NotNull DescriptorListLoadingContext loadingContext,
                                                                       @NotNull Element child) {
    String ns = child.getAttributeValue("defaultExtensionNs");
    Interner<String> stringInterner = loadingContext.getStringInterner();
    for (Element extensionElement : child.getChildren()) {
      String os = extensionElement.getAttributeValue("os");
      if (os != null) {
        extensionElement.removeAttribute("os");
        if (!isSuitableForOs(os)) {
          continue;
        }
      }

      String qualifiedExtensionPointName = stringInterner.intern(ExtensionsAreaImpl.extractPointName(extensionElement, ns));
      ContainerDescriptor containerDescriptor;
      switch (qualifiedExtensionPointName) {
        case APPLICATION_SERVICE:
          containerDescriptor = descriptor.myAppContainerDescriptor;
          break;
        case PROJECT_SERVICE:
          containerDescriptor = descriptor.myProjectContainerDescriptor;
          break;
        case MODULE_SERVICE:
          containerDescriptor = descriptor.myModuleContainerDescriptor;
          break;
        default:
          if (epNameToExtensions == null) {
            epNameToExtensions = new LinkedHashMap<>();
          }

          List<Element> list = epNameToExtensions.get(qualifiedExtensionPointName);
          if (list == null) {
            list = new SmartList<>();
            epNameToExtensions.put(qualifiedExtensionPointName, list);
          }
          list.add(extensionElement);
          continue;
      }

      containerDescriptor.addService(readServiceDescriptor(extensionElement, loadingContext));
    }
    return epNameToExtensions;
  }

  /**
   * EP cannot be added directly to root descriptor, because probably later EP list will be ignored if dependency plugin is not available.
   * So, we use rootDescriptor as plugin id (because descriptor plugin id is null - it is not plugin descriptor, but optional config descriptor)
   * and for BeanExtensionPoint/InterfaceExtensionPoint (because instances will be used only if merged).
   *
   * And descriptor as data container.
   */
  static void readExtensionPoints(@NotNull IdeaPluginDescriptorImpl rootDescriptor, @NotNull IdeaPluginDescriptorImpl descriptor, @NotNull Element parentElement) {
    for (Content child : parentElement.getContent()) {
      if (!(child instanceof Element)) {
        continue;
      }

      Element element = (Element)child;

      String area = element.getAttributeValue(ATTRIBUTE_AREA);
      ContainerDescriptor containerDescriptor;
      if (area == null) {
        containerDescriptor = descriptor.myAppContainerDescriptor;
      }
      else {
        if ("IDEA_PROJECT".equals(area)) {
          containerDescriptor = descriptor.myProjectContainerDescriptor;
        }
        else if ("IDEA_MODULE".equals(area)) {
          containerDescriptor = descriptor.myModuleContainerDescriptor;
        }
        else {
          PluginManagerCore.getLogger().error("Unknown area: " + area);
          continue;
        }
      }

      String pointName = getExtensionPointName(element, rootDescriptor.getPluginId());

      String beanClassName = element.getAttributeValue("beanClass");
      String interfaceClassName = element.getAttributeValue("interface");
      if (beanClassName == null && interfaceClassName == null) {
        throw new RuntimeException("Neither 'beanClass' nor 'interface' attribute is specified for extension point '" + pointName + "' in '" + rootDescriptor.getPluginId() + "' plugin");
      }

      if (beanClassName != null && interfaceClassName != null) {
        throw new RuntimeException("Both 'beanClass' and 'interface' attributes are specified for extension point '" + pointName + "' in '" + rootDescriptor.getPluginId() + "' plugin");
      }

      boolean dynamic = Boolean.parseBoolean(element.getAttributeValue("dynamic"));
      ExtensionPointImpl<Object> point;
      if (interfaceClassName == null) {
        point = new BeanExtensionPoint<>(pointName, beanClassName, rootDescriptor, dynamic);
      }
      else {
        point = new InterfaceExtensionPoint<>(pointName, interfaceClassName, rootDescriptor, dynamic);
      }

      List<ExtensionPointImpl<?>> result = containerDescriptor.extensionPoints;
      if (result == null) {
        result = new ArrayList<>();
        containerDescriptor.extensionPoints = result;
      }
      result.add(point);
    }
  }

  private static @NotNull String getExtensionPointName(@NotNull Element extensionPointElement, @NotNull PluginId effectivePluginId) {
    String pointName = extensionPointElement.getAttributeValue("qualifiedName");
    if (pointName == null) {
      String name = extensionPointElement.getAttributeValue("name");
      if (name == null) {
        throw new RuntimeException("'name' attribute not specified for extension point in '" + effectivePluginId + "' plugin");
      }

      pointName = effectivePluginId.getIdString() + '.' + name;
    }
    return pointName;
  }
}
