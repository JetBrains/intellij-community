package com.intellij.conversion.impl;

import com.intellij.conversion.CannotConvertException;
import com.intellij.conversion.ComponentManagerSettings;
import com.intellij.conversion.ModuleSettings;
import com.intellij.facet.FacetManagerImpl;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.impl.convert.JDomConvertingUtil;
import com.intellij.openapi.module.impl.ModuleImpl;
import com.intellij.openapi.roots.impl.*;
import com.intellij.openapi.roots.impl.ModuleLibraryOrderEntryImpl;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class ModuleSettingsImpl extends ComponentManagerSettingsImpl implements ModuleSettings {
  private String myModuleName;
  @NonNls private static final String MODULE_ROOT_MANAGER_COMPONENT = "NewModuleRootManager";

  public ModuleSettingsImpl(File moduleFile, ConversionContextImpl context) throws CannotConvertException {
    super(moduleFile, context);
    myModuleName = StringUtil.trimEnd(moduleFile.getName(), ModuleFileType.DOT_DEFAULT_EXTENSION);
  }

  @NotNull
  public String getModuleName() {
    return myModuleName;
  }

  @Nullable
  public String getModuleType() {
    return getRootElement().getAttributeValue(ModuleImpl.ELEMENT_TYPE);
  }

  @NotNull
  public File getModuleFile() {
    return mySettingsFile.getFile();
  }

  @NotNull
  public Collection<? extends Element> getFacetElements(@NotNull String facetTypeId) {
    final Element facetManager = getComponentElement(FacetManagerImpl.COMPONENT_NAME);
    final ArrayList<Element> elements = new ArrayList<Element>();
    for (Element child : JDomConvertingUtil.getChildren(facetManager, FacetManagerImpl.FACET_ELEMENT)) {
      if (facetTypeId.equals(child.getAttributeValue(FacetManagerImpl.TYPE_ATTRIBUTE))) {
        elements.add(child);
      }
    }
    return elements;
  }

  public void setModuleType(@NotNull String moduleType) {
    getRootElement().setAttribute(ModuleImpl.ELEMENT_TYPE, moduleType);
  }

  @NotNull
  public String expandPath(@NotNull String path) {
    return myContext.expandPath(path, this); 
  }

  @NotNull
  public Collection<File> getSourceRoots(boolean includeTests) {
    final List<File> result = new ArrayList<File>();
    for (Element contentRoot : getContentRootElements()) {
      for (Element sourceFolder : JDomConvertingUtil.getChildren(contentRoot, SourceFolderImpl.ELEMENT_NAME)) {
        boolean isTestFolder = Boolean.parseBoolean(sourceFolder.getAttributeValue(SourceFolderImpl.TEST_SOURCE_ATTR));
        if (includeTests || !isTestFolder) {
          result.add(getFile(sourceFolder.getAttributeValue(SourceFolderImpl.URL_ATTRIBUTE)));
        }
      }
    }
    return result;
  }

  private List<Element> getContentRootElements() {
    return JDomConvertingUtil.getChildren(getComponentElement(MODULE_ROOT_MANAGER_COMPONENT), ContentEntryImpl.ELEMENT_NAME);
  }

  @NotNull
  public Collection<File> getContentRoots() {
    final List<File> result = new ArrayList<File>();
    for (Element contentRoot : getContentRootElements()) {
      String path = VfsUtil.urlToPath(contentRoot.getAttributeValue(ContentEntryImpl.URL_ATTRIBUTE));
      result.add(new File(FileUtil.toSystemDependentName(expandPath(path))));
    }
    return result;
  }

  public void addExcludedFolder(@NotNull File directory) {
    final ComponentManagerSettings rootManagerSettings = myContext.getProjectRootManagerSettings();
    if (rootManagerSettings != null) {
      final Element projectRootManager = rootManagerSettings.getComponentElement("ProjectRootManager");
      if (projectRootManager != null) {
        final Element outputElement = projectRootManager.getChild("output");
        if (outputElement != null) {
          final String outputUrl = outputElement.getAttributeValue("url");
          if (outputUrl != null) {
            final File outputFile = getFile(outputUrl);
            try {
              if (FileUtil.isAncestor(outputFile, directory, false)) {
                return;
              }
            }
            catch (IOException ignored) {
            }
          }
        }
      }
    }
    for (Element contentRoot : getContentRootElements()) {
      final File root = getFile(contentRoot.getAttributeValue(ContentEntryImpl.URL_ATTRIBUTE));
      try {
        if (FileUtil.isAncestor(root, directory, true)) {
          addExcludedFolder(directory, contentRoot);
        }
      }
      catch (IOException ignored) {
      }
    }
  }

  public List<File> getModuleLibraryRootUrls(String libraryName) {
    final Element component = getComponentElement(MODULE_ROOT_MANAGER_COMPONENT);
    for (Element element : JDomConvertingUtil.getChildren(component, OrderEntryFactory.ORDER_ENTRY_ELEMENT_NAME)) {
      if (ModuleLibraryOrderEntryImpl.ENTRY_TYPE.equals(element.getAttributeValue(OrderEntryFactory.ORDER_ENTRY_TYPE_ATTR))) {
        final Element library = element.getChild(LibraryImpl.ELEMENT);
        if (library != null && libraryName.equals(library.getAttributeValue(LibraryImpl.LIBRARY_NAME_ATTR))) {
          return myContext.getClassRoots(library, this);
        }
      }
    }
    return Collections.emptyList();
  }

  private void addExcludedFolder(File directory, Element contentRoot) throws IOException {
    for (Element excludedFolder : JDomConvertingUtil.getChildren(contentRoot, ExcludeFolderImpl.ELEMENT_NAME)) {
      final File excludedDir = getFile(excludedFolder.getAttributeValue(ExcludeFolderImpl.URL_ATTRIBUTE));
      if (FileUtil.isAncestor(excludedDir, directory, false)) {
        return;
      }
    }
    String path = myContext.collapsePath(FileUtil.toSystemIndependentName(directory.getAbsolutePath()), this);
    contentRoot.addContent(new Element(ExcludeFolderImpl.ELEMENT_NAME).setAttribute(ExcludeFolderImpl.URL_ATTRIBUTE, VfsUtil.pathToUrl(path)));
  }

  private File getFile(String url) {
    return new File(FileUtil.toSystemDependentName(expandPath(VfsUtil.urlToPath(url))));
  }
}
