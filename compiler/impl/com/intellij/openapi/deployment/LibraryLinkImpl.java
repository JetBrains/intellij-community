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
package com.intellij.openapi.deployment;

import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.PathUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LibraryLinkImpl extends LibraryLink {
  private static final Map<PackagingMethod, String> methodToDescriptionForDirs = new HashMap<PackagingMethod, String>();
  private static final Map<PackagingMethod, String> methodToDescriptionForFiles = new HashMap<PackagingMethod, String>();
  @NonNls static final String LEVEL_ATTRIBUTE_NAME = "level";
  @NonNls static final String URL_ELEMENT_NAME = "url";
  @NonNls private static final String TEMP_ELEMENT_NAME = "temp";
  @NonNls static final String NAME_ATTRIBUTE_NAME = "name";

  @NonNls private static final String JAR_SUFFIX = ".jar";

  static {
    methodToDescriptionForDirs.put(PackagingMethod.DO_NOT_PACKAGE, CompilerBundle.message("packaging.method.description.do.not.package"));
    methodToDescriptionForDirs.put(PackagingMethod.COPY_FILES, CompilerBundle.message("packaging.method.description.copy.directories"));
    methodToDescriptionForDirs.put(PackagingMethod.JAR_AND_COPY_FILE, CompilerBundle.message("packaging.method.description.jar.and.copy.file"));
    methodToDescriptionForDirs.put(PackagingMethod.JAR_AND_COPY_FILE_AND_LINK_VIA_MANIFEST, CompilerBundle.message("packaging.method.description.jar.and.copy.file.and.link.via.manifest"));

    methodToDescriptionForFiles.put(PackagingMethod.DO_NOT_PACKAGE, CompilerBundle.message("packaging.method.description.do.not.package"));
    methodToDescriptionForFiles.put(PackagingMethod.COPY_FILES, CompilerBundle.message("packaging.method.description.copy.files"));
    methodToDescriptionForFiles.put(PackagingMethod.COPY_FILES_AND_LINK_VIA_MANIFEST, CompilerBundle.message("packaging.method.description.copy.files.and.link.via.manifest"));
  }

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.deployment.LibraryLink");
  private LibraryInfo myLibraryInfo;
  @Nullable
  private final Project myProject;

  public LibraryLinkImpl(@Nullable Library library, @NotNull Module parentModule) {
    this(library, parentModule.getProject(), parentModule);
  }

  public LibraryLinkImpl(@Nullable Library library, @Nullable Project project, @Nullable Module parentModule) {
    super(parentModule);
    myProject = project;
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
    final Library library = info.findLibrary(myProject, getParentModule(), provider);
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
    final LibraryTable table = findTable(levelName, myProject);
    return table == null ? "???" : table.getPresentation().getDisplayName(false);
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

  public boolean hasDirectoriesOnly() {
    boolean hasDirsOnly = true;
    final Library library = getLibrary();
    if (library != null) {
      final VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
      for (VirtualFile file : files) {
        if (file != null && !VfsUtil.virtualToIoFile(file).isDirectory()) {
          hasDirsOnly = false;
          break;
        }
      }
    } else {
      final List<String> urls = getUrls();
      for (final String url : urls) {
        VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
        if (file != null && !VfsUtil.virtualToIoFile(file).isDirectory()) {
          hasDirsOnly = false;
          break;
        }
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

    List<String> urls = getUrls();
    if (LibraryLink.MODULE_LEVEL.equals(getLevel()) && urls.size() == 1) {
      String jarName = getJarFileName(urls.get(0));
      if (jarName != null) {
        String outputPath = getURI();
        if (outputPath != null) {
          int nameIndex = outputPath.lastIndexOf('/');
          if (outputPath.substring(nameIndex + 1).equals(jarName)) {
            if (nameIndex <= 0) {
              setURI("/");
            }
            else {
              setURI(outputPath.substring(0, nameIndex));
            }
          }
        }
      }
    }
  }

  @Nullable
  private static String getJarFileName(final String url) {
    if (!url.endsWith(JarFileSystem.JAR_SEPARATOR)) return null;

    String path = url.substring(0, url.length() - JarFileSystem.JAR_SEPARATOR.length());
    String jarName = path.substring(path.lastIndexOf('/') + 1);
    return jarName.endsWith(JAR_SUFFIX) ? jarName : null;
  }

  public void writeExternal(Element element) throws WriteExternalException {
    List<String> urls = getUrls();

    String outputPath = getURI();
    if (outputPath != null && !outputPath.endsWith(JAR_SUFFIX) && LibraryLink.MODULE_LEVEL.equals(getLevel()) && urls.size() == 1
      && (getPackagingMethod().equals(PackagingMethod.COPY_FILES) || getPackagingMethod().equals(PackagingMethod.COPY_FILES_AND_LINK_VIA_MANIFEST))) {
      String jarName = getJarFileName(urls.get(0));
      if (jarName != null) {
        //for compatibility with builds before 7119
        setURI(DeploymentUtil.appendToPath(outputPath, jarName));
      }
    }
    super.writeExternal(element);
    setURI(outputPath);

    String name = getName();
    if (name == null) {
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

  public boolean resolveElement(ModulesProvider provider, final FacetsProvider facetsProvider) {
    return getLibrary(provider) != null;

  }

  public LibraryLink clone() {
    LibraryLink libraryLink = DeploymentUtil.getInstance().createLibraryLink(getLibrary(), getParentModule());
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

  public void adjustLibrary() {
    if (myLibraryInfo instanceof LibraryInfoBasedOnLibrary) {
      Library library = ((LibraryInfoBasedOnLibrary)myLibraryInfo).getLibrary();
      LibraryTable table = library.getTable();
      if (table != null && !isInTable(library, table)) {
        LibraryInfoImpl info = new LibraryInfoImpl(library);
        Library newLibrary = info.findLibrary(myProject, getParentModule(), null);
        myLibraryInfo = newLibrary != null ? new LibraryInfoBasedOnLibrary(newLibrary) : info;
      }
    }
  }

  private static boolean isInTable(final Library library, final LibraryTable table) {
    for (Library aLibrary : table.getLibraries()) {
      if (aLibrary == library) {
        return true;
      }
    }
    return false;
  }
}
