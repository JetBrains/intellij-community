/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.project.JarData;
import com.intellij.openapi.externalSystem.service.project.ExternalLibraryPathTypeMapper;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.externalSystem.service.project.PlatformFacade;
import com.intellij.openapi.externalSystem.model.project.LibraryPathType;
import com.intellij.openapi.externalSystem.model.project.id.LibraryId;

import java.io.File;
import java.util.*;

/**
 * @author Denis Zhdanov
 * @since 12/13/12 1:04 PM
 */
public class ExternalJarManager {

  private static final Logger LOG = Logger.getInstance("#" + ExternalJarManager.class.getName());

  @NotNull private final PlatformFacade                myPlatformFacade;
  @NotNull private final ExternalLibraryPathTypeMapper myLibraryPathTypeMapper;

  public ExternalJarManager(@NotNull PlatformFacade facade, @NotNull ExternalLibraryPathTypeMapper mapper) {
    myPlatformFacade = facade;
    myLibraryPathTypeMapper = mapper;
  }

  public void importJars(@NotNull final Collection<? extends JarData> jars, @NotNull final Project project, boolean synchronous) {
    if (jars.isEmpty()) {
      return;
    }
    final Map<LibraryId, List<JarData>> jarsByLibraries = ContainerUtilRt.newHashMap();
    for (JarData jar : jars) {
      List<JarData> list = jarsByLibraries.get(jar.getLibraryId());
      if (list == null) {
        jarsByLibraries.put(jar.getLibraryId(), list = ContainerUtilRt.newArrayList());
      }
      list.add(jar);
    }
    ExternalSystemUtil.executeProjectChangeAction(project, jars, synchronous, new Runnable() {
      @Override
      public void run() {
        for (Map.Entry<LibraryId, List<JarData>> entry : jarsByLibraries.entrySet()) {
          LibraryTable table = myPlatformFacade.getProjectLibraryTable(project);
          Library library = table.getLibraryByName(entry.getKey().getLibraryName());
          if (library == null) {
            return;
          }
          Library.ModifiableModel model = library.getModifiableModel();
          try {
            LocalFileSystem fileSystem = LocalFileSystem.getInstance();
            for (JarData jar : entry.getValue()) {
              OrderRootType ideJarType = myLibraryPathTypeMapper.map(jar.getPathType());
              for (VirtualFile file : model.getFiles(ideJarType)) {
                if (jar.getPath().equals(ExternalSystemUtil.getLocalFileSystemPath(file))) {
                  return;
                }
              }

              File jarFile = new File(jar.getPath());
              VirtualFile virtualFile = fileSystem.refreshAndFindFileByIoFile(jarFile);
              if (virtualFile == null) {
                LOG.warn(
                  String.format("Can't find a jar of the library '%s' at path '%s'", jar.getLibraryId().getLibraryName(), jar.getPath())
                );
                return;
              }
              if (virtualFile.isDirectory()) {
                model.addRoot(virtualFile, ideJarType);
              }
              else {
                VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(virtualFile);
                if (jarRoot == null) {
                  LOG.warn(String.format(
                    "Can't parse contents of the jar file at path '%s' for the library '%s''", jar.getPath(), library.getName()
                  ));
                  return;
                }
                model.addRoot(jarRoot, ideJarType);
              }
            }
          }
          finally {
            model.commit();
          }
        }
      }
    });
  }

  public void removeJars(@NotNull Collection<? extends JarData> jars, @NotNull Project project, boolean synchronous) {
    if (jars.isEmpty()) {
      return;
    }
    Map<LibraryId, List<JarData>> jarsByLibraries = ContainerUtilRt.newHashMap();
    for (JarData jar : jars) {
      List<JarData> list = jarsByLibraries.get(jar.getLibraryId());
      if (list == null) {
        jarsByLibraries.put(jar.getLibraryId(), list = ContainerUtilRt.newArrayList());
      }
      list.add(jar);
    }

    LibraryTable libraryTable = myPlatformFacade.getProjectLibraryTable(project);
    for (Map.Entry<LibraryId, List<JarData>> entry : jarsByLibraries.entrySet()) {
      Library library = libraryTable.getLibraryByName(entry.getKey().getLibraryName());
      if (library == null) {
        continue;
      }
      Set<JarData> libraryJars = ContainerUtilRt.newHashSet(entry.getValue());
      for (JarData jar : entry.getValue()) {
        boolean valid = false;
        for (VirtualFile file : library.getFiles(myLibraryPathTypeMapper.map(jar.getPathType()))) {
          if (jar.getPath().equals(ExternalSystemUtil.getLocalFileSystemPath(file))) {
            valid = true;
            break;
          }
        }
        if (!valid) {
          libraryJars.remove(jar);
        }
      }

      if (!libraryJars.isEmpty()) {
        removeLibraryJars(libraryJars, project, synchronous);
      }
    }
  }

  /**
   * Removes given jars from IDE project structure assuming that they belong to the same library.
   * 
   * @param jars     jars to remove
   * @param project  current project
   */
  private void removeLibraryJars(@NotNull final Set<JarData> jars, @NotNull final Project project, boolean synchronous) {
    ExternalSystemUtil.executeProjectChangeAction(project, jars, synchronous, new Runnable() {
      @Override
      public void run() {
        LibraryTable libraryTable = myPlatformFacade.getProjectLibraryTable(project);
        LibraryId libraryId = jars.iterator().next().getLibraryId();
        Library library = libraryTable.getLibraryByName(libraryId.getLibraryName());
        if (library == null) {
          return;
        }
        Set<String> pathsToRemove = ContainerUtil.map2Set(jars, new Function<JarData, String>() {
          @Override
          public String fun(JarData jar) {
            return jar.getPath();
          }
        });
        Library.ModifiableModel model = library.getModifiableModel();
        try {
          for (LibraryPathType gradlePathType : LibraryPathType.values()) {
            OrderRootType idePathType = myLibraryPathTypeMapper.map(gradlePathType);
            for (VirtualFile file : model.getFiles(idePathType)) {
              if (pathsToRemove.contains(ExternalSystemUtil.getLocalFileSystemPath(file))) {
                model.removeRoot(file.getUrl(), idePathType);
              }
            }
          }
        }
        finally {
          model.commit();
        }
      }
    });
  }
}
