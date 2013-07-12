package com.intellij.compilerOutputIndex.api.fs;

import com.intellij.openapi.compiler.CompilerPaths;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public final class CompilerOutputFilesUtil {

  private CompilerOutputFilesUtil() {}

  public final static String CLASS_FILES_SUFFIX = ".class";

  public static void iterateProjectClassFiles(@NotNull final Project project, @NotNull final Consumer<File> fileConsumer) {
    for (final Module module : ModuleManager.getInstance(project).getModules()) {
      iterateModuleClassFiles(module, fileConsumer);
    }
  }

  public static void iterateModuleClassFiles(@NotNull final Module module, @NotNull final Consumer<File> fileConsumer) {
    final VirtualFile moduleOutputDirectory = CompilerPaths.getModuleOutputDirectory(module, false);
    if (moduleOutputDirectory == null) {
      return;
    }
    final String canonicalPath = moduleOutputDirectory.getCanonicalPath();
    if (canonicalPath == null) {
      return;
    }
    final File root = new File(canonicalPath);
    iterateClassFilesOverRoot(root, fileConsumer);
  }

  public static void iterateClassFilesOverRoot(@NotNull final File file, final Consumer<File> fileConsumer) {
    iterateClassFilesOverRoot(file, fileConsumer, new HashSet<File>());
  }

  private static void iterateClassFilesOverRoot(@NotNull final File file, final Consumer<File> fileConsumer, final Set<File> visited) {
    if (file.isDirectory()) {
      final File[] files = file.listFiles();
      if (files != null) {
        for (final File childFile : files) {
          if (visited.add(childFile)) {
            iterateClassFilesOverRoot(childFile.getAbsoluteFile(), fileConsumer, visited);
          }
        }
      }
    }
    else {
      if (file.getName().endsWith(CLASS_FILES_SUFFIX)) {
        fileConsumer.consume(file);
      }
    }
  }
}
