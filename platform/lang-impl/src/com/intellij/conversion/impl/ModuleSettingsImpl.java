// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.conversion.impl;

import com.intellij.conversion.CannotConvertException;
import com.intellij.conversion.ComponentManagerSettings;
import com.intellij.conversion.ModuleSettings;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.JDomSerializationUtil;
import org.jetbrains.jps.model.serialization.facet.JpsFacetSerializer;
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer;
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

@ApiStatus.Internal
public final class ModuleSettingsImpl extends SettingsXmlFile implements ModuleSettings {
  private final String myModuleName;
  private final ConversionContextImpl context;

  ModuleSettingsImpl(@NotNull Path moduleFile, @NotNull ConversionContextImpl context) throws CannotConvertException {
    super(moduleFile);

    myModuleName = Strings.trimEnd(moduleFile.getFileName().toString(), ModuleFileType.DOT_DEFAULT_EXTENSION);
    this.context = context;
  }

  @Override
  public @NotNull String getModuleName() {
    return myModuleName;
  }

  @Override
  public @Nullable String getModuleType() {
    return getRootElement().getAttributeValue(Module.ELEMENT_TYPE);
  }

  @Override
  public @NotNull Collection<Element> getFacetElements(@NotNull String facetTypeId) {
    Element facetManager = getComponentElement(JpsFacetSerializer.FACET_MANAGER_COMPONENT_NAME);
    ArrayList<Element> elements = new ArrayList<>();
    addFacetTypes(facetTypeId, facetManager, elements);
    return elements;
  }

  private static void addFacetTypes(@NotNull String facetTypeId, @Nullable Element parent, @NotNull ArrayList<? super Element> elements) {
    for (Element child : JDOMUtil.getChildren(parent, JpsFacetSerializer.FACET_TAG)) {
      if (facetTypeId.equals(child.getAttributeValue(JpsFacetSerializer.TYPE_ATTRIBUTE))) {
        elements.add(child);
      }
      else {
        addFacetTypes(facetTypeId, child, elements);
      }
    }
  }

  @Override
  public Element getFacetElement(@NotNull String facetTypeId) {
    return ContainerUtil.getFirstItem(getFacetElements(facetTypeId), null);
  }

  @Override
  public void addFacetElement(@NotNull String facetTypeId, @NotNull String facetName, Element configuration) {
    Element componentElement = JDomSerializationUtil.findOrCreateComponentElement(getRootElement(), JpsFacetSerializer.FACET_MANAGER_COMPONENT_NAME);
    Element facetElement = new Element(JpsFacetSerializer.FACET_TAG);
    facetElement.setAttribute(JpsFacetSerializer.TYPE_ATTRIBUTE, facetTypeId);
    facetElement.setAttribute(JpsFacetSerializer.NAME_ATTRIBUTE, facetName);
    configuration.setName(JpsFacetSerializer.CONFIGURATION_TAG);
    facetElement.addContent(configuration);
    componentElement.addContent(facetElement);
  }

  @Override
  public void setModuleType(@NotNull String moduleType) {
    getRootElement().setAttribute(Module.ELEMENT_TYPE, moduleType);
  }

  @Override
  public @NotNull String expandPath(@NotNull String path) {
    return context.expandPath(path, this);
  }

  @Override
  public @NotNull String collapsePath(@NotNull String path) {
    return ConversionContextImpl.collapsePath(path, this);
  }

  @Override
  public @NotNull Collection<File> getSourceRoots(boolean includeTests) {
    final List<File> result = new ArrayList<>();
    for (Element contentRoot : getContentRootElements()) {
      for (Element sourceFolder : JDOMUtil.getChildren(contentRoot, JpsModuleRootModelSerializer.SOURCE_FOLDER_TAG)) {
        boolean isTestFolder = Boolean.parseBoolean(sourceFolder.getAttributeValue(JpsModuleRootModelSerializer.IS_TEST_SOURCE_ATTRIBUTE));
        if (includeTests || !isTestFolder) {
          result.add(getFile(sourceFolder.getAttributeValue(JpsModuleRootModelSerializer.URL_ATTRIBUTE)));
        }
      }
    }
    return result;
  }

  private List<Element> getContentRootElements() {
    return JDOMUtil.getChildren(getComponentElement(MODULE_ROOT_MANAGER_COMPONENT), JpsModuleRootModelSerializer.CONTENT_TAG);
  }

  @Override
  public @NotNull Collection<File> getContentRoots() {
    final List<File> result = new ArrayList<>();
    for (Element contentRoot : getContentRootElements()) {
      String path = VfsUtilCore.urlToPath(contentRoot.getAttributeValue(JpsModuleRootModelSerializer.URL_ATTRIBUTE));
      result.add(new File(FileUtil.toSystemDependentName(expandPath(path))));
    }
    return result;
  }

  @Override
  public @Nullable String getProjectOutputUrl() {
    final ComponentManagerSettings rootManagerSettings = context.getProjectRootManagerSettings();
    final Element projectRootManager = rootManagerSettings == null ? null : rootManagerSettings.getComponentElement("ProjectRootManager");
    final Element outputElement = projectRootManager == null ? null : projectRootManager.getChild("output");
    return outputElement == null ? null : outputElement.getAttributeValue("url");
  }

  @Override
  public void addExcludedFolder(@NotNull File directory) {
    final ComponentManagerSettings rootManagerSettings = context.getProjectRootManagerSettings();
    if (rootManagerSettings != null) {
      final Element projectRootManager = rootManagerSettings.getComponentElement("ProjectRootManager");
      if (projectRootManager != null) {
        final Element outputElement = projectRootManager.getChild("output");
        if (outputElement != null) {
          final String outputUrl = outputElement.getAttributeValue("url");
          if (outputUrl != null) {
            final File outputFile = getFile(outputUrl);
            if (FileUtil.isAncestor(outputFile, directory, false)) {
              return;
            }
          }
        }
      }
    }
    for (Element contentRoot : getContentRootElements()) {
      final File root = getFile(contentRoot.getAttributeValue(JpsModuleRootModelSerializer.URL_ATTRIBUTE));
      if (FileUtil.isAncestor(root, directory, true)) {
        addExcludedFolder(directory, contentRoot);
      }
    }
  }

  @Override
  public @NotNull List<Path> getModuleLibraryRoots(String libraryName) {
    Element library = findModuleLibraryElement(libraryName);
    return library == null ? Collections.emptyList() : context.getClassRootPaths(library, this);
  }

  @Override
  public boolean hasModuleLibrary(String libraryName) {
    return findModuleLibraryElement(libraryName) != null;
  }

  private @Nullable Element findModuleLibraryElement(String libraryName) {
    for (Element element : getOrderEntries()) {
      if (JpsModuleRootModelSerializer.MODULE_LIBRARY_TYPE.equals(element.getAttributeValue(JpsModuleRootModelSerializer.TYPE_ATTRIBUTE))) {
        final Element library = element.getChild(JpsLibraryTableSerializer.LIBRARY_TAG);
        if (library != null && libraryName.equals(library.getAttributeValue(JpsLibraryTableSerializer.NAME_ATTRIBUTE))) {
          return library;
        }
      }
    }
    return null;
  }

  @Override
  public List<Element> getOrderEntries() {
    final Element component = getComponentElement(MODULE_ROOT_MANAGER_COMPONENT);
    return JDOMUtil.getChildren(component, JpsModuleRootModelSerializer.ORDER_ENTRY_TAG);
  }

  @Override
  public @NotNull Collection<ModuleSettings> getAllModuleDependencies() {
    Set<ModuleSettings> dependencies = new HashSet<>();
    collectDependencies(dependencies);
    return dependencies;
  }

  private void collectDependencies(Set<ModuleSettings> dependencies) {
    if (!dependencies.add(this)) {
      return;
    }

    for (Element element : getOrderEntries()) {
      if (JpsModuleRootModelSerializer.MODULE_TYPE.equals(element.getAttributeValue(JpsModuleRootModelSerializer.TYPE_ATTRIBUTE))) {
        final String moduleName = element.getAttributeValue(JpsModuleRootModelSerializer.MODULE_NAME_ATTRIBUTE);
        if (moduleName != null) {
          final ModuleSettings moduleSettings = context.getModuleSettings(moduleName);
          if (moduleSettings != null) {
            ((ModuleSettingsImpl)moduleSettings).collectDependencies(dependencies);
          }
        }
      }
    }
  }

  private void addExcludedFolder(File directory, Element contentRoot) {
    for (Element excludedFolder : JDOMUtil.getChildren(contentRoot, JpsModuleRootModelSerializer.EXCLUDE_FOLDER_TAG)) {
      final File excludedDir = getFile(excludedFolder.getAttributeValue(JpsModuleRootModelSerializer.URL_ATTRIBUTE));
      if (FileUtil.isAncestor(excludedDir, directory, false)) {
        return;
      }
    }
    String path = ConversionContextImpl.collapsePath(FileUtil.toSystemIndependentName(directory.getAbsolutePath()), this);
    contentRoot.addContent(new Element(JpsModuleRootModelSerializer.EXCLUDE_FOLDER_TAG).setAttribute(JpsModuleRootModelSerializer.URL_ATTRIBUTE, VfsUtilCore.pathToUrl(path)));
  }

  private File getFile(String url) {
    return new File(FileUtil.toSystemDependentName(expandPath(VfsUtilCore.urlToPath(url))));
  }
}