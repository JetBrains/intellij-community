// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Provides additional compiler options for a given module.
 * <p>
 * Not an actual extension. Used to avoid dependency on the compiler module.
 */
public abstract class JavaCompilerConfigurationProxy {
  private static final ExtensionPointName<JavaCompilerConfigurationProxy> EP_NAME = new ExtensionPointName<>("com.intellij.javaCompilerConfigurationProxy");

  /**
   * Returns additional compiler options applicable to the given module, if any.
   * @see JavaCompilerConfigurationProxy#setAdditionalOptions(Project, Module, List)
   */
  abstract protected List<String> getAdditionalOptionsImpl(@NotNull Project project, @NotNull Module module);

  /**
   * Sets additional compiler options applicable to the given module.
   * @see JavaCompilerConfigurationProxy#getAdditionalOptions(Project, Module)
   */
  abstract protected void setAdditionalOptionsImpl(@NotNull Project project, @NotNull Module module, @NotNull List<String> options);

  public static List<String> getAdditionalOptions(@NotNull Project project, @NotNull Module module) {
    JavaCompilerConfigurationProxy[] extensions = EP_NAME.getExtensions();
    if (extensions.length == 0) return Collections.emptyList();
    return extensions[0].getAdditionalOptionsImpl(project, module);
  }

  public static void setAdditionalOptions(@NotNull Project project, @NotNull Module module, @NotNull List<String> options) {
    JavaCompilerConfigurationProxy[] extensions = EP_NAME.getExtensions();
    if (extensions.length == 0) return;
    extensions[0].setAdditionalOptionsImpl(project, module, options);
  }

  /**
   * @param targetModuleName  jigsaw module name
   * @param module            ij module with compiler settings
   * @param root              current source root
   * @return                  true if compiler settings for the module contains --patch-module=targetModuleName=root
   */
  public static boolean isPatchedModuleRoot(@NotNull String targetModuleName, @NotNull Module module, @NotNull VirtualFile root) {
    List<String> options = getAdditionalOptions(module.getProject(), module);
    if (options.isEmpty()) {
      return false;
    }

    String prefix = targetModuleName + "=";
    for (String option : optionValues(options, "--patch-module")) {
      if (option.startsWith(prefix)) {
        String[] patchingPaths = option.substring(prefix.length()).split(File.pathSeparator);
        for (String patchingPath : patchingPaths) {
          if (VfsUtilCore.pathEqualsTo(root, FileUtil.toSystemIndependentName(patchingPath))) {
            return true;
          }
        }
      }
    }
    return false;
  }
  
  public static List<String> optionValues(List<String> options, String name){
    if (options.isEmpty()) {
      return Collections.emptyList();
    }
    else {
      boolean useValue = false;
      List<String> result = new ArrayList<>();
      for (String option : options) {
        if (option.equals(name)) {
          useValue = true;
          continue;
        }
        else if (useValue) {
          useValue = false;
        }
        else if (option.startsWith(name) && option.length() > name.length() + 1 && option.charAt(name.length()) == '=') {
          option = option.substring(name.length() + 1);
        }
        else {
          continue;
        }
        if (!option.isEmpty()) {
          result.add(option);
        }
      }
      return result;
    }
  }
}
