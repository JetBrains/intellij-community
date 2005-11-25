/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.compiler.ant;

import com.intellij.compiler.ant.taskdefs.Path;
import com.intellij.compiler.ant.taskdefs.PathElement;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: Nov 25, 2004
 */
public class LibraryDefinitionsGeneratorFactory {
  private final ProjectEx myProject;
  private GenerationOptions myGenOptions;
  private Set<String> myUsedLibraries = new HashSet<String>();

  public LibraryDefinitionsGeneratorFactory(ProjectEx project, GenerationOptions genOptions) {
    myProject = project;
    myGenOptions = genOptions;
    final ModuleManager moduleManager = ModuleManager.getInstance(project);
    final Module[] modules = moduleManager.getModules();
    for (Module module : modules) {
      final OrderEntry[] orderEntries = ModuleRootManager.getInstance(module).getOrderEntries();
      for (OrderEntry orderEntry : orderEntries) {
        if (orderEntry instanceof LibraryOrderEntry && orderEntry.isValid()) {
          Library library = ((LibraryOrderEntry)orderEntry).getLibrary();
          if (library != null) {
            final String name = library.getName();
            if (name != null) {
              myUsedLibraries.add(name);
            }
          }
        }
      }
    }
  }

  public Generator create(LibraryTable libraryTable, File baseDir, final String comment) {
    final Library[] libraries = libraryTable.getLibraries();
    if (libraries.length == 0) {
      return null;
    }

    final CompositeGenerator gen = new CompositeGenerator();

    gen.add(new Comment(comment), 1);

    for (final Library library : libraries) {
      final String libraryName = library.getName();
      if (!myUsedLibraries.contains(libraryName)) {
        continue;
      }

      final VirtualFile[] files = library.getFiles(OrderRootType.COMPILATION_CLASSES);
      final Path libraryPath = new Path(BuildProperties.getLibraryPathId(libraryName));
      for (int i = 0; i < files.length; i++) {
        final VirtualFile file = files[i];
        libraryPath.add(new PathElement(GenerationUtils.toRelativePath(file, baseDir, BuildProperties.getProjectBaseDirProperty(),
                                                                       myGenOptions, !myProject.isSavePathsRelative())));
      }
      gen.add(libraryPath, 1);
    }
    return gen.getGeneratorCount() > 0? gen : null;
  }


}
