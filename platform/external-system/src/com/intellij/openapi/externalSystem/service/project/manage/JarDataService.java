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
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.JarData;
import com.intellij.openapi.externalSystem.model.project.LibraryData;
import com.intellij.openapi.externalSystem.service.project.ExternalLibraryPathTypeMapper;
import com.intellij.openapi.externalSystem.service.project.ProjectStructureHelper;
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
public class JarDataService implements ProjectDataService<JarData> {

  private static final Logger LOG = Logger.getInstance("#" + JarDataService.class.getName());

  @NotNull private final PlatformFacade                myPlatformFacade;
  @NotNull private final ProjectStructureHelper        myProjectStructureHelper;
  @NotNull private final ExternalLibraryPathTypeMapper myLibraryPathTypeMapper;

  public JarDataService(@NotNull PlatformFacade facade,
                        @NotNull ProjectStructureHelper helper,
                        @NotNull ExternalLibraryPathTypeMapper mapper)
  {
    myPlatformFacade = facade;
    myProjectStructureHelper = helper;
    myLibraryPathTypeMapper = mapper;
  }

  @NotNull
  @Override
  public Key<JarData> getTargetDataKey() {
    return ProjectKeys.JAR;
  }

  @Override
  public void importData(@NotNull Collection<DataNode<JarData>> toImport, @NotNull Project project, boolean synchronous) {
    if (toImport.isEmpty()) {
      return;
    }

    Map<DataNode<LibraryData>, Collection<DataNode<JarData>>> byLibrary = ExternalSystemUtil.groupBy(toImport, ProjectKeys.LIBRARY);
    for (Map.Entry<DataNode<LibraryData>, Collection<DataNode<JarData>>> entry : byLibrary.entrySet()) {
      Library library = myProjectStructureHelper.findIdeLibrary(entry.getKey().getData(), project);
      if (library == null) {
        LOG.warn(String.format(
          "Can't import jars %s. Reason: target library (%s) is not configured at the ide and can't be imported",
          entry.getValue(), entry.getKey().getData().getName()
        ));
        return;
      }
      importJars(entry.getValue(), library, entry.getKey().getData().getOwner(), project, synchronous);
    }
  }

  public void importJars(@NotNull final Collection<DataNode<JarData>> jars,
                         @NotNull final Library library,
                         @NotNull ProjectSystemId externalSystemId,
                         @NotNull final Project project,
                         boolean synchronous)
  {
    if (jars.isEmpty()) {
      return;
    }

    ExternalSystemUtil.executeProjectChangeAction(project, externalSystemId, jars, synchronous, new Runnable() {
      @Override
      public void run() {
        LibraryTable table = myPlatformFacade.getProjectLibraryTable(project);
        String libraryName = library.getName();
        if (libraryName == null) {
          LOG.warn(String.format("Can't import jars %s. Reason: target library doesn't have a name", jars));
          return;
        }
        Library libraryToUse = table.getLibraryByName(libraryName);
        if (libraryToUse == null) {
          LOG.warn(String.format("Can't import jars %s. Reason: target library (%s) doesn't exist", jars, libraryName));
          return;
        }
        Library.ModifiableModel model = library.getModifiableModel();
        try {
          LocalFileSystem fileSystem = LocalFileSystem.getInstance();
          for (DataNode<JarData> jarNode : jars) {
            JarData jarData = jarNode.getData();
            OrderRootType ideJarType = myLibraryPathTypeMapper.map(jarData.getPathType());
            for (VirtualFile file : model.getFiles(ideJarType)) {
              if (jarData.getPath().equals(ExternalSystemUtil.getLocalFileSystemPath(file))) {
                return;
              }
            }

            File jarFile = new File(jarData.getPath());
            VirtualFile virtualFile = fileSystem.refreshAndFindFileByIoFile(jarFile);
            if (virtualFile == null) {
              LOG.warn(String.format(
                "Can't find a jar of the library '%s' at path '%s'",
                jarData.getLibraryId().getLibraryName(), jarData.getPath()
              ));
              return;
            }
            if (virtualFile.isDirectory()) {
              model.addRoot(virtualFile, ideJarType);
            }
            else {
              VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(virtualFile);
              if (jarRoot == null) {
                LOG.warn(String.format(
                  "Can't parse contents of the jar file at path '%s' for the library '%s''",
                  jarData.getPath(), libraryName
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
    });
  }

  @Override
  public void removeData(@NotNull Collection<DataNode<JarData>> toRemove, @NotNull Project project, boolean synchronous) {
    if (toRemove.isEmpty()) {
      return;
    }
    Map<LibraryId, List<DataNode<JarData>>> jarsByLibraries = ContainerUtilRt.newHashMap();
    for (DataNode<JarData> jarNode : toRemove) {
      LibraryId libraryId = jarNode.getData().getLibraryId();
      List<DataNode<JarData>> list = jarsByLibraries.get(libraryId);
      if (list == null) {
        jarsByLibraries.put(libraryId, list = ContainerUtilRt.newArrayList());
      }
      list.add(jarNode);
    }

    LibraryTable libraryTable = myPlatformFacade.getProjectLibraryTable(project);
    for (Map.Entry<LibraryId, List<DataNode<JarData>>> entry : jarsByLibraries.entrySet()) {
      Library library = libraryTable.getLibraryByName(entry.getKey().getLibraryName());
      if (library == null) {
        continue;
      }
      Set<DataNode<JarData>> libraryJars = ContainerUtilRt.newHashSet(entry.getValue());
      for (DataNode<JarData> jarNode : entry.getValue()) {
        boolean valid = false;
        JarData jarData = jarNode.getData();
        for (VirtualFile file : library.getFiles(myLibraryPathTypeMapper.map(jarData.getPathType()))) {
          if (jarData.getPath().equals(ExternalSystemUtil.getLocalFileSystemPath(file))) {
            valid = true;
            break;
          }
        }
        if (!valid) {
          libraryJars.remove(jarNode);
        }
      }

      if (!libraryJars.isEmpty()) {
        removeLibraryJars(libraryJars, entry.getKey(), project, synchronous);
      }
    }
  }

  /**
   * Removes given jars from IDE project structure assuming that they belong to the same library.
   * 
   * @param toRemove     jars to remove
   * @param project  current project
   */
  private void removeLibraryJars(@NotNull final Set<DataNode<JarData>> toRemove,
                                 @NotNull final LibraryId libraryId,
                                 @NotNull final Project project,
                                 boolean synchronous)
  {
    ExternalSystemUtil.executeProjectChangeAction(project, ProjectSystemId.IDE, toRemove, synchronous, new Runnable() {
      @Override
      public void run() {
        LibraryTable libraryTable = myPlatformFacade.getProjectLibraryTable(project);
        Library library = libraryTable.getLibraryByName(libraryId.getLibraryName());
        if (library == null) {
          return;
        }
        Set<String> pathsToRemove = ContainerUtil.map2Set(toRemove, new Function<DataNode<JarData>, String>() {
          @Override
          public String fun(DataNode<JarData> node) {
            return node.getData().getPath();
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
