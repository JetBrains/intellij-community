package com.intellij.j2ee.module;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;

import java.util.HashSet;
import java.util.List;


/**
 * @author Alexey Kudravtsev
 */
public abstract class LibraryLink extends ContainerElement {

  public static final String MODULE_LEVEL = "module";

  public LibraryLink(Module parentModule) {
    super(parentModule);
  }

  public abstract Library getLibrary();

  public abstract void addUrl(String url);

  public abstract List<String> getUrls();

  public abstract String getSingleFileName();

  public abstract boolean hasDirectoriesOnly();

  public abstract String getName();

  public abstract String getLevel();

  protected abstract Module[] getAllDependentModules();

  protected abstract void addDependencies(Module module, HashSet<Module> result);


  public static Library findLibrary(String libraryName, String libraryLevel, Project project) {
    if (libraryName == null) {
      return null;
    }

    LibraryTable table = findTable(libraryLevel, project);
    if (table == null) {
      return null;
    }
    else {
      return table.getLibraryByName(libraryName);
    }
  }

  private static LibraryTable findTable(String libraryLevel, Project project) {
    if (libraryLevel == null) return null;

    return LibraryTablesRegistrar.getInstance().getLibraryTableByLevel(libraryLevel, project);
  }

  public static Library findModuleLibrary(Module module, String url) {
    if (url == null) return null;

    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    OrderEntry[] orderEntries = moduleRootManager.getOrderEntries();
    for (int i = 0; i < orderEntries.length; i++) {
      OrderEntry orderEntry = orderEntries[i];
      if (orderEntry instanceof LibraryOrderEntry){
        LibraryOrderEntry libraryOrderEntry = ((LibraryOrderEntry)orderEntry);
        Library library = libraryOrderEntry.getLibrary();
        if (library == null) continue;
        String[] urls = library.getUrls(OrderRootType.CLASSES);
        if (urls.length != 1) continue;
        if (Comparing.strEqual(urls[0], url)) return library;
      }
    }
    return null;
  }
}
