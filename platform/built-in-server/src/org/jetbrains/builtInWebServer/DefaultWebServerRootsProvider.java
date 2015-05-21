/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.builtInWebServer;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.roots.impl.DirectoryInfo;
import com.intellij.openapi.roots.impl.ModuleLibraryOrderEntryImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PairFunction;
import com.intellij.util.PlatformUtils;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class DefaultWebServerRootsProvider extends WebServerRootsProvider {
  private static final NotNullLazyValue<OrderRootType[]> ORDER_ROOT_TYPES = new NotNullLazyValue<OrderRootType[]>() {
    @NotNull
    @Override
    protected OrderRootType[] compute() {
      OrderRootType javaDocRootType;
      try {
        javaDocRootType = JavadocOrderRootType.getInstance();
      }
      catch (Throwable e) {
        javaDocRootType = null;
      }

      return javaDocRootType == null
             ? new OrderRootType[]{OrderRootType.DOCUMENTATION, OrderRootType.SOURCES, OrderRootType.CLASSES}
             : new OrderRootType[]{javaDocRootType, OrderRootType.DOCUMENTATION, OrderRootType.SOURCES, OrderRootType.CLASSES};
    }
  };

  @Nullable
  @Override
  public PathInfo resolve(@NotNull String path, @NotNull Project project) {
    PairFunction<String, VirtualFile, VirtualFile> resolver;
    if (PlatformUtils.isIntelliJ()) {
      int index = path.indexOf('/');
      if (index > 0 && !path.regionMatches(!SystemInfo.isFileSystemCaseSensitive, 0, project.getName(), 0, index)) {
        String moduleName = path.substring(0, index);
        AccessToken token = ReadAction.start();
        Module module;
        try {
          module = ModuleManager.getInstance(project).findModuleByName(moduleName);
        }
        finally {
          token.finish();
        }

        if (module != null && !module.isDisposed()) {
          path = path.substring(index + 1);
          resolver = WebServerPathToFileManager.getInstance(project).getResolver(path);

          ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
          PathInfo result = findByRelativePath(path, moduleRootManager.getSourceRoots(), resolver, moduleName);
          if (result == null) {
            result = findByRelativePath(path, moduleRootManager.getContentRoots(), resolver, moduleName);
            if (result == null) {
              result = findInModuleLibraries(path, module, resolver);
            }
          }
          if (result != null) {
            return result;
          }
        }
      }
    }

    Module[] modules;
    AccessToken token = ReadAction.start();
    try {
      modules = ModuleManager.getInstance(project).getModules();
    }
    finally {
      token.finish();
    }

    resolver = WebServerPathToFileManager.getInstance(project).getResolver(path);
    PathInfo result = findByRelativePath(project, path, modules, true, resolver);
    if (result == null) {
      // let's find in content roots
      result = findByRelativePath(project, path, modules, false, resolver);
      if (result == null) {
        return findInLibraries(project, modules, path, resolver);
      }
    }
    return result;
  }

  @Nullable
  private static PathInfo findInModuleLibraries(@NotNull String path, @NotNull Module module, @NotNull PairFunction<String, VirtualFile, VirtualFile> resolver) {
    int index = path.indexOf('/');
    if (index <= 0) {
      return null;
    }

    Ref<PathInfo> result = Ref.create();
    findInModuleLibraries(resolver, path.substring(0, index), path.substring(index + 1), result, module);
    return result.get();
  }

  @Nullable
  private static PathInfo findInLibraries(@NotNull Project project,
                                          @NotNull Module[] modules,
                                          @NotNull String path,
                                          @NotNull PairFunction<String, VirtualFile, VirtualFile> resolver) {
    int index = path.indexOf('/');
    if (index < 0) {
      return null;
    }

    String libraryFileName = path.substring(0, index);
    String relativePath = path.substring(index + 1);
    AccessToken token = ReadAction.start();
    try {
      Ref<PathInfo> result = Ref.create();
      for (Module module : modules) {
        if (!module.isDisposed()) {
          if (findInModuleLibraries(resolver, libraryFileName, relativePath, result, module)) {
            return result.get();
          }
        }
      }

      for (Library library : LibraryTablesRegistrar.getInstance().getLibraryTable(project).getLibraries()) {
        PathInfo pathInfo = findInLibrary(libraryFileName, relativePath, library, resolver);
        if (pathInfo != null) {
          return pathInfo;
        }
      }
    }
    finally {
      token.finish();
    }

    return null;
  }

  private static boolean findInModuleLibraries(@NotNull final PairFunction<String, VirtualFile, VirtualFile> resolver,
                                               @NotNull final String libraryFileName,
                                               @NotNull final String relativePath,
                                               @NotNull final Ref<PathInfo> result,
                                               @NotNull Module module) {
    ModuleRootManager.getInstance(module).orderEntries().forEachLibrary(new Processor<Library>() {
      @Override
      public boolean process(Library library) {
        result.set(findInLibrary(libraryFileName, relativePath, library, resolver));
        return result.isNull();
      }
    });
    return !result.isNull();
  }

  @Nullable
  private static PathInfo findInLibrary(@NotNull String libraryFileName,
                                        @NotNull String relativePath,
                                        @NotNull Library library,
                                        @NotNull PairFunction<String, VirtualFile, VirtualFile> resolver) {
    for (OrderRootType rootType : ORDER_ROOT_TYPES.getValue()) {
      for (VirtualFile root : library.getFiles(rootType)) {
        if (StringUtil.equalsIgnoreCase(root.getNameSequence(), libraryFileName)) {
          VirtualFile file = resolver.fun(relativePath, root);
          if (file != null) {
            return new PathInfo(file, root, null, true);
          }
        }
      }
    }
    return null;
  }

  @Nullable
  @Override
  public PathInfo getRoot(@NotNull VirtualFile file, @NotNull Project project) {
    AccessToken token = ReadAction.start();
    try {
      DirectoryIndex directoryIndex = DirectoryIndex.getInstance(project);
      DirectoryInfo info = directoryIndex.getInfoForFile(file);
      // we serve excluded files
      if (!info.isExcluded() && !info.isInProject()) {
        // javadoc jars is "not under project", but actually is, so, let's check project library table
        return file.getFileSystem() == JarFileSystem.getInstance() ? getInfoForDocJar(file, project) : null;
      }

      VirtualFile root = info.getSourceRoot();
      boolean isLibrary;
      if (root == null) {
        root = info.getContentRoot();
        if (root == null) {
          root = info.getLibraryClassRoot();
          isLibrary = true;

          assert root != null : file.getPresentableUrl();
        }
        else {
          isLibrary = false;
        }
      }
      else {
        isLibrary = info.isInLibrarySource();
      }

      Module module = info.getModule();
      if (isLibrary && module == null) {
        for (OrderEntry entry : directoryIndex.getOrderEntries(info)) {
          if (entry instanceof ModuleLibraryOrderEntryImpl) {
            module = entry.getOwnerModule();
            break;
          }
        }
      }

      return new PathInfo(file, root, getModuleNameQualifier(project, module), isLibrary);
    }
    finally {
      token.finish();
    }
  }

  @Nullable
  private static PathInfo getInfoForDocJar(@NotNull final VirtualFile file, @NotNull final Project project) {
    final OrderRootType javaDocRootType = JavadocOrderRootType.getInstance();
    if (javaDocRootType == null) {
      return null;
    }

    class LibraryProcessor implements Processor<Library> {
      PathInfo result;
      Module module;

      @Override
      public boolean process(Library library) {
        for (VirtualFile root : library.getFiles(javaDocRootType)) {
          if (VfsUtilCore.isAncestor(root, file, false)) {
            result = new PathInfo(file, root, getModuleNameQualifier(project, module), true);
            return false;
          }
        }
        return true;
      }
    }

    LibraryProcessor processor = new LibraryProcessor();
    AccessToken token = ReadAction.start();
    try {
      ModuleManager moduleManager = ModuleManager.getInstance(project);
      for (Module module : moduleManager.getModules()) {
        if (module.isDisposed()) {
          continue;
        }

        processor.module = module;
        ModuleRootManager.getInstance(module).orderEntries().forEachLibrary(processor);
        if (processor.result != null) {
          return processor.result;
        }
      }

      processor.module = null;
      for (Library library : LibraryTablesRegistrar.getInstance().getLibraryTable(project).getLibraries()) {
        if (!processor.process(library)) {
          return processor.result;
        }
      }
    }
    finally {
      token.finish();
    }

    return null;
  }

  @Nullable
  private static String getModuleNameQualifier(@NotNull Project project, @Nullable Module module) {
    if (module != null &&
        PlatformUtils.isIntelliJ() &&
        !(module.getName().equalsIgnoreCase(project.getName()) || BuiltInWebServer.compareNameAndProjectBasePath(module.getName(), project))) {
      return module.getName();
    }
    return null;
  }

  @Nullable
  private static PathInfo findByRelativePath(@NotNull String path,
                                             @NotNull VirtualFile[] roots,
                                             @NotNull PairFunction<String, VirtualFile, VirtualFile> resolver,
                                             @Nullable String moduleName) {
    for (VirtualFile root : roots) {
      VirtualFile file = resolver.fun(path, root);
      if (file != null) {
        return new PathInfo(file, root, moduleName, false);
      }
    }
    return null;
  }

  @Nullable
  private static PathInfo findByRelativePath(@NotNull Project project,
                                             @NotNull String path,
                                             @NotNull Module[] modules,
                                             boolean inSourceRoot,
                                             @NotNull PairFunction<String, VirtualFile, VirtualFile> resolver) {
    for (Module module : modules) {
      if (!module.isDisposed()) {
        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
        PathInfo result = findByRelativePath(path, inSourceRoot ? moduleRootManager.getSourceRoots() : moduleRootManager.getContentRoots(), resolver, null);
        if (result != null) {
          result.moduleName = getModuleNameQualifier(project, module);
          return result;
        }
      }
    }
    return null;
  }
}