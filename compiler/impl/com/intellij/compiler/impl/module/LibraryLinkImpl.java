/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.compiler.impl.module;

import com.intellij.javaee.make.MakeUtil;
import com.intellij.javaee.serverInstances.ApplicationServersManager;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.module.PackagingMethod;
import com.intellij.openapi.compiler.module.ContainerElement;
import com.intellij.openapi.compiler.module.LibraryLink;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.PathUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LibraryLinkImpl extends LibraryLink {
  private static final Map<PackagingMethod, String> methodToDescriptionForDirs = new HashMap<PackagingMethod, String>();
  private static final Map<PackagingMethod, String> methodToDescriptionForFiles = new HashMap<PackagingMethod, String>();
  @NonNls private static final String LEVEL_ATTRIBUTE_NAME = "level";
  @NonNls private static final String URL_ELEMENT_NAME = "url";
  @NonNls private static final String TEMP_ELEMENT_NAME = "temp";
  @NonNls private static final String NAME_ATTRIBUTE_NAME = "name";

  static {
    methodToDescriptionForDirs.put(PackagingMethod.DO_NOT_PACKAGE, CompilerBundle.message("packaging.method.description.do.not.package"));
    methodToDescriptionForDirs.put(PackagingMethod.COPY_FILES, CompilerBundle.message("packaging.method.description.copy.directories"));
    methodToDescriptionForDirs.put(PackagingMethod.JAR_AND_COPY_FILE, CompilerBundle.message("packaging.method.description.jar.and.copy.file"));
    methodToDescriptionForDirs.put(PackagingMethod.JAR_AND_COPY_FILE_AND_LINK_VIA_MANIFEST, CompilerBundle.message("packaging.method.description.jar.and.copy.file.and.link.via.manifest"));

    methodToDescriptionForFiles.put(PackagingMethod.DO_NOT_PACKAGE, CompilerBundle.message("packaging.method.description.do.not.package"));
    methodToDescriptionForFiles.put(PackagingMethod.COPY_FILES, CompilerBundle.message("packaging.method.description.copy.files"));
    methodToDescriptionForFiles.put(PackagingMethod.COPY_FILES_AND_LINK_VIA_MANIFEST, CompilerBundle.message("packaging.method.description.copy.files.and.link.via.manifest"));
  }

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.compiler.module.LibraryLink");
  private LibraryInfo myLibraryInfo;

  public LibraryLinkImpl(Library library, Module parentModule) {
    super(parentModule);
    if (library == null) {
      myLibraryInfo = new LibraryInfoImpl();
    }
    else {
      myLibraryInfo = new LibraryInfoBasedOnLibrary(library);
    }

  }

  public @Nullable Library getLibrary() {
    return getLibrary(null);
  }

  public @Nullable Library getLibrary(@Nullable ModulesProvider provider) {
    if (myLibraryInfo instanceof LibraryInfoBasedOnLibrary) {
      return ((LibraryInfoBasedOnLibrary)myLibraryInfo).getLibrary();
    }

    LOG.assertTrue(myLibraryInfo instanceof LibraryInfoImpl);
    final LibraryInfoImpl info = ((LibraryInfoImpl)myLibraryInfo);
    final Library library = info.findLibrary(getParentModule(), provider);
    if (library != null) {
      myLibraryInfo = new LibraryInfoBasedOnLibrary(library);
    }

    return library;
  }

  public String toString() {
    return CompilerBundle.message("library.link.string.presentation.presentablename.to.uri", getPresentableName(), getURI());
  }

  public String getPresentableName() {
    if (getName() != null) return getName();
    List<String> urls = myLibraryInfo.getUrls();
    if (urls.size() == 0) return CompilerBundle.message("linrary.link.empty.library.presentable.name");
    final String url = urls.get(0);
    final String path = PathUtil.toPresentableUrl(url);

    return FileUtil.toSystemDependentName(path);
  }

  public String getDescription() {
    String levelName = myLibraryInfo.getLevel();
    if (levelName.equals(MODULE_LEVEL)) {
      return CompilerBundle.message("library.link.description.module.library");
    }
    else if (LibraryTablesRegistrar.APPLICATION_LEVEL.equals(levelName)) {
      return CompilerBundle.message("library.link.description.global.library");
    }
    else if (LibraryTablesRegistrar.PROJECT_LEVEL.equals(levelName)) {
      return CompilerBundle.message("library.link.description.project.library");
    }
    else if (ApplicationServersManager.APPLICATION_SERVER_MODULE_LIBRARIES.equals(levelName)) {
      return CompilerBundle.message("library.link.description.application.server.library");
    }
    else {
      return "???";
    }
  }

  public String getDescriptionForPackagingMethod(PackagingMethod method) {
    if (hasDirectoriesOnly()) {
      final String text = methodToDescriptionForDirs.get(method);
      return text != null ? text : methodToDescriptionForFiles.get(method);
    }
    else {
      final String text = methodToDescriptionForFiles.get(method);
      return text != null ? text : methodToDescriptionForDirs.get(method);
    }
  }

  public void addUrl(String url) {
    myLibraryInfo.addUrl(url);
  }

  public List<String> getUrls() {
    return myLibraryInfo.getUrls();
  }

  public boolean equalsIgnoreAttributes(ContainerElement otherElement) {
    if (!(otherElement instanceof LibraryLink)) return false;
    final LibraryLink otherLibraryLink = (LibraryLink)otherElement;
    if (!Comparing.strEqual(getName(), otherLibraryLink.getName())) return false;
    return getUrls().equals(otherLibraryLink.getUrls());
  }

  public String getSingleFileName() {
    // non-module-level libs can contain multiple files
    final String table = getLevel();
    if (!MODULE_LEVEL.equals(table)) return null;

    List<String> urls = getUrls();
    if (urls.size() != 1) return null;
    File file = new File(PathUtil.toPresentableUrl(urls.get(0)));
    return file.getName();
  }

  public boolean hasDirectoriesOnly() {
    List<String> urls = getUrls();
    boolean hasDirsOnly = true;
    for (final String url : urls) {
      VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
      VirtualFile localFile = file == null ? null :
                              LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(PathUtil.getLocalPath(file)));
      if (localFile != null && !localFile.isDirectory()) {
        hasDirsOnly = false;
        break;
      }
    }
    return hasDirsOnly;
  }

  public String getName() {
    return myLibraryInfo.getName();
  }

  public String getLevel() {
    return myLibraryInfo.getLevel();
  }

  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    myLibraryInfo.readExternal(element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    String name = getName();
    if (name == null) {
      List<String> urls = getUrls();
      for (final String url : urls) {
        final Element urlElement = new Element(URL_ELEMENT_NAME);
        urlElement.setText(url);
        element.addContent(urlElement);
      }
    }
    else {
      element.setAttribute(NAME_ATTRIBUTE_NAME, name);
    }
    if (getLevel() != null) {
      element.setAttribute(LEVEL_ATTRIBUTE_NAME, getLevel());
    }
  }

  public boolean resolveElement(ModulesProvider provider) {
    return getLibrary(provider) != null;

  }

  public LibraryLink clone() {
    LibraryLink libraryLink = MakeUtil.getInstance().createLibraryLink(getLibrary(), getParentModule());
    Element temp = new Element(TEMP_ELEMENT_NAME);
    try {
      writeExternal(temp);
      libraryLink.readExternal(temp);
    }
    catch (Exception e) {
      LOG.error(e);
    }
    return libraryLink;
  }

  private static @Nullable Library findModuleLibrary(Module module, @Nullable ModulesProvider provider, @Nullable String name, String url) {
    if (url == null && name == null) return null;
    if (provider == null) {
      provider = new DefaultModulesProvider(module.getProject());
    }
    return findModuleLibrary(module, provider, url, name, new HashSet<Module>());
  }

  private static @Nullable Library findModuleLibrary(Module module, final @NotNull ModulesProvider provider,
                                                     String url, @Nullable String name, Set<Module> visited) {
    if (!visited.add(module)) {
      return null;
    }

    ModuleRootModel rootModel = provider.getRootModel(module);
    OrderEntry[] orderEntries = rootModel.getOrderEntries();
    for (OrderEntry orderEntry : orderEntries) {
      if (orderEntry instanceof ModuleOrderEntry) {
        final Module dependency = ((ModuleOrderEntry)orderEntry).getModule();
        if (dependency != null) {
          final Library library = findModuleLibrary(dependency, provider, url, name, visited);
          if (library != null) {
            return library;
          }
        }
      }
      else if (orderEntry instanceof LibraryOrderEntry) {
        LibraryOrderEntry libraryOrderEntry = ((LibraryOrderEntry)orderEntry);
        Library library = libraryOrderEntry.getLibrary();
        if (library == null) continue;

        if (name != null && library.getTable()==null && name.equals(library.getName())) {
          return library;
        }

        if (name == null) {
          String[] urls = library.getUrls(OrderRootType.CLASSES);
          if (urls.length == 1 && Comparing.strEqual(urls[0], url)) return library;
        }
      }
    }
    return null;
  }

  interface LibraryInfo {
    String getName();

    @NotNull List<String> getUrls();

    String getLevel();

    void addUrl(String url);

    void readExternal(Element element) throws InvalidDataException;
  }

  private static class LibraryInfoImpl implements LibraryInfo {
    private final List<String> myUrls = new ArrayList<String>();
    private String myName;
    private String myLevel;

    public String getName() {
      return myName;
    }

    @NotNull
    public List<String> getUrls() {
      return myUrls;
    }

    public String getLevel() {
      return myLevel;
    }

    public void addUrl(String url) {
      myUrls.clear();
      myUrls.add(url);
    }

    @Nullable
    public Library findLibrary(final Module module, final @Nullable ModulesProvider provider) {
      if (module == null) return null;

      if (MODULE_LEVEL.equals(myLevel)) {
        String url = myUrls.size() == 1 ? myUrls.get(0) : null;
        return findModuleLibrary(module, provider, myName, url);
      }
      else {
        return LibraryLink.findLibrary(myName, myLevel, module.getProject());
      }

    }

    public void readExternal(Element element) throws InvalidDataException {
      myName = element.getAttributeValue(NAME_ATTRIBUTE_NAME);

      myLevel = element.getAttributeValue(LEVEL_ATTRIBUTE_NAME);
      myUrls.clear();
      final List urls = element.getChildren(URL_ELEMENT_NAME);
      for (Object url1 : urls) {
        Element url = (Element)url1;
        myUrls.add(url.getText());
      }
    }
  }

  private static class LibraryInfoBasedOnLibrary implements LibraryInfo {
    private final Library myLibrary;

    private LibraryInfoBasedOnLibrary(Library library) {
      myLibrary = library;
    }

    public String getName() {
      return myLibrary.getName();
    }

    @NotNull
    public List<String> getUrls() {
      return Arrays.asList(myLibrary.getUrls(OrderRootType.CLASSES));
    }

    public String getLevel() {
      if (myLibrary.getTable() == null) {
        return MODULE_LEVEL;
      }
      else {
        return myLibrary.getTable().getTableLevel();
      }
    }

    public Library getLibrary() {
      return myLibrary;
    }

    public void addUrl(String url) {
    }

    public void readExternal(Element element) throws InvalidDataException {
    }

  }
}
