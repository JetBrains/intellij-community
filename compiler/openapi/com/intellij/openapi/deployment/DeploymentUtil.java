/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.deployment;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.make.BuildRecipe;
import com.intellij.openapi.compiler.make.PackagingFileFilter;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.descriptors.ConfigFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Set;
import java.util.jar.Manifest;

public abstract class DeploymentUtil {
  public static DeploymentUtil getInstance() {
    return ServiceManager.getService(DeploymentUtil.class);
  }

  public abstract boolean addModuleOutputContents(@NotNull CompileContext context,
                                                  @NotNull BuildRecipe items,
                                                  @NotNull Module sourceModule,
                                                  Module targetModule,
                                                  @NonNls String outputRelativePath,
                                                  @NonNls String possibleBaseOuputPath,
                                                  @Nullable PackagingFileFilter fileFilter);

  public abstract void addLibraryLink(@NotNull CompileContext context,
                                      @NotNull BuildRecipe items,
                                      @NotNull LibraryLink libraryLink,
                                      @NotNull Module module,
                                      String possibleBaseOutputPath);

  public abstract void copyFile(@NotNull File fromFile,
                                @NotNull File toFile,
                                @NotNull CompileContext context,
                                @Nullable Set<String> writtenPaths,
                                @Nullable FileFilter fileFilter) throws IOException;

  public abstract boolean addItemsRecursively(@NotNull BuildRecipe items,
                                              @NotNull File root,
                                              @NotNull Module module,
                                              String outputRelativePath,
                                              @Nullable PackagingFileFilter fileFilter,
                                              String possibleBaseOutputPath);

  public static void reportRecursiveCopying(CompileContext context, final String sourceDirPath, String targetDirPath,
                                            final String dirTitle, String additionalMessage) {
    final String message = CompilerBundle.message(
      "message.text.copy.dirTitle.dirPath.to.targetDirPath.will.lead.to.recursive.copying.additionalMessage", dirTitle,
      FileUtil.toSystemDependentName(sourceDirPath), FileUtil.toSystemDependentName(targetDirPath), additionalMessage);
    context.addMessage(CompilerMessageCategory.ERROR, message,null,-1,-1);
  }

  public static String trimForwardSlashes(@NotNull String path) {
    while (path.length() != 0 && (path.charAt(0) == '/' || path.charAt(0) == File.separatorChar)) {
      path = path.substring(1);
    }
    return path;
  }

  public static String trimTrailingSlashes(@NotNull String path) {
    int l = path.length() - 1;
    while (l >= 0 && (path.charAt(l) == '/' || path.charAt(l) == File.separatorChar)) l--;
    return path.substring(0, l+1);
  }

  public abstract void reportDeploymentDescriptorDoesNotExists(ConfigFile descriptor, CompileContext context, Module module);

  @Nullable public abstract Manifest createManifest(@NotNull BuildRecipe buildRecipe);

  public abstract void addJavaModuleOutputs(@NotNull Module module,
                                            @NotNull ModuleLink[] containingModules,
                                            @NotNull BuildRecipe instructions,
                                            @NotNull CompileContext context,
                                            String explodedPath);

  public static boolean checkFileExists(final File file, CompileContext context) {
    if (!file.exists()) {
      context.addMessage(CompilerMessageCategory.ERROR, CompilerBundle.message("compiler.message.text.file.does.not.exist", file),null,-1,-1);
      return false;
    }
    return true;
  }

  public static String concatPaths(String... paths) {
    final StringBuilder builder = new StringBuilder();
    for (String path : paths) {
      if (path.length() == 0) continue;

      final int len = builder.length();
      if (len > 0 && builder.charAt(len - 1) != '/' && builder.charAt(len - 1) != File.separatorChar) {
        builder.append('/');
      }
      builder.append(len != 0 ? trimForwardSlashes(path) : path);
    }
    return builder.toString();
  }

  public static String appendToPath(@NotNull String path, @NotNull String name) {
    if (!StringUtil.endsWithChar(path, '/') && !path.endsWith(File.separator)) {
      path += "/";
    }
    return path + trimForwardSlashes(name);
  }

  public static File canonicalRelativePath(File file, final String outputRelativePath) {
    return new File(PathUtil.getCanonicalPath(appendToPath(file.getPath(),outputRelativePath)));
  }

  public abstract ModuleLink createModuleLink(Module dep, Module module);

  public abstract LibraryLink createLibraryLink(Library library, @NotNull Module parentModule);

  public abstract PackagingConfiguration createPackagingConfiguration(@NotNull Module module);

  public abstract BuildRecipe createBuildRecipe();

  public @Nullable abstract ContainerElement findElementByOrderEntry(PackagingConfiguration packagingConfiguration, OrderEntry entry);

  @Nullable
  public abstract String getConfigFileErrorMessage(ConfigFile configFile);

  @Nullable
  public static String getRelativePath(File baseDir, File file) {
    if (baseDir == null || file == null) return null;

    String basePath = baseDir.getAbsolutePath();
    final String filePath = file.getAbsolutePath();
    return getRelativePath(basePath, filePath);
  }

  @Nullable public static String getRelativePath(@NotNull String basePath, @NotNull final String filePath) {
    if (basePath.equals(filePath)) return "";
    if (!basePath.endsWith(File.separator)) basePath += File.separatorChar;

    int len = 0;
    int lastSeparatorIndex = 0; // need this for cases like this: base="/temp/abcde/baseDir" and file="/temp/ab"
    while (len < filePath.length() && len < basePath.length() && filePath.charAt(len) == basePath.charAt(len)) {
      if (basePath.charAt(len) == File.separatorChar) {
        lastSeparatorIndex = len;
      }
      len++;
    }

    if (len == 0) {
      return null;
    }
    final StringBuilder relativePath = StringBuilderSpinAllocator.alloc();
    try {
      for (int i=len; i < basePath.length(); i++) {
        if (basePath.charAt(i) == File.separatorChar) {
          relativePath.append("..");
          relativePath.append(File.separatorChar);
        }
      }
      relativePath.append(filePath.substring(lastSeparatorIndex + 1));

      return relativePath.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(relativePath);
    }
  }

  public abstract @Nullable File findUserSuppliedManifestFile(@NotNull BuildRecipe buildRecipe);

  public abstract void checkConfigFile(final ConfigFile descriptor, final CompileContext compileContext, final Module module);
}
