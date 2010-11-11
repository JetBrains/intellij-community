package com.intellij.compiler;

import com.intellij.compiler.impl.TranslatingCompilerFilesMonitor;
import com.intellij.compiler.impl.javaCompiler.javac.JavacSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Arrays;
import java.util.List;

/**
 * @author nik
 */
public class CompilerTestUtil {
  private CompilerTestUtil() {
  }

  public static void setupJavacForTests(Project project) {
    CompilerConfigurationImpl compilerConfiguration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(project);
    compilerConfiguration.projectOpened();
    compilerConfiguration.setDefaultCompiler(compilerConfiguration.getJavacCompiler());

    JavacSettings javacSettings = JavacSettings.getInstance(project);
    javacSettings.setTestsUseExternalCompiler(true);
  }

  public static void scanSourceRootsToRecompile(Project project) {
    // need this to emulate project opening
    final List<VirtualFile> roots = Arrays.asList(ProjectRootManager.getInstance(project).getContentSourceRoots());
    TranslatingCompilerFilesMonitor.getInstance().scanSourceContent(new TranslatingCompilerFilesMonitor.ProjectRef(project), roots, roots.size(), true);
  }
}
