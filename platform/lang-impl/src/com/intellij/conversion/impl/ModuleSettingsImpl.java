package com.intellij.conversion.impl;

import com.intellij.conversion.CannotConvertException;
import com.intellij.conversion.ModuleSettings;
import com.intellij.facet.FacetManagerImpl;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.impl.convert.JDomConvertingUtil;
import com.intellij.openapi.module.impl.ModuleImpl;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.roots.impl.ContentEntryImpl;
import com.intellij.openapi.roots.impl.SourceFolderImpl;
import com.intellij.openapi.vfs.VfsUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public class ModuleSettingsImpl extends ComponentManagerSettingsImpl implements ModuleSettings {
  private String myModuleName;

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
    final Element rootManager = getComponentElement("NewModuleRootManager");
    final List<File> result = new ArrayList<File>();
    for (Element contentRoot : JDomConvertingUtil.getChildren(rootManager, ContentEntryImpl.ELEMENT_NAME)) {
      for (Element sourceFolder : JDomConvertingUtil.getChildren(contentRoot, SourceFolderImpl.ELEMENT_NAME)) {
        boolean isTestFolder = Boolean.parseBoolean(sourceFolder.getAttributeValue(SourceFolderImpl.TEST_SOURCE_ATTR));
        if (includeTests || !isTestFolder) {
          final String path = VfsUtil.urlToPath(sourceFolder.getAttributeValue(SourceFolderImpl.URL_ATTR));
          result.add(new File(FileUtil.toSystemDependentName(expandPath(path))));
        }
      }
    }
    return result;
  }
}
