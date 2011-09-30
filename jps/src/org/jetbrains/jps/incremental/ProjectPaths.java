package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.*;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/30/11
 */
public class ProjectPaths {
  @NotNull
  private final Project myProject;
  @Nullable
  private final File myProjectTargetDir;

  public ProjectPaths(Project project) {
    this(project, null);
  }

  public ProjectPaths(Project project, @Nullable File projectTargetDir) {
    myProject = project;
    myProjectTargetDir = projectTargetDir;
  }

  public Collection<File> getClasspath(ClasspathKind kind, Module module) {
    final Set<File> files = new TreeSet<File>();
    if (kind.isRuntime()) {
      collectRuntimeClasspath(module, kind, files, new HashSet<Module>());
    }
    else {
      collectCompileClasspath(module, kind, files, new HashSet<Module>(), false);
    }
    return files;
  }

  public Collection<File> getClasspath(ClasspathKind kind, ModuleChunk chunk) {
    final Set<File> files = new TreeSet<File>();
    final Set<Module> processedModules = new HashSet<Module>();
    for (Module module : chunk.getModules()) {
      if (kind.isRuntime()) {
        collectRuntimeClasspath(module, kind, files, processedModules);
      }
      else {
        collectCompileClasspath(module, kind, files, processedModules, false);
      }
    }
    return files;
  }

  private void collectCompileClasspath(Module module, ClasspathKind kind, Set<File> classpath, Set<Module> processed, boolean exportedOnly) {
    if (!processed.contains(module)) {
      processed.add(module);
      // IMPORTANT! assuming that ModuleSource order entry is included in module.getClassPath()
      for (ClasspathItem it : module.getClasspath(kind)) {
        final boolean shouldAdd = !exportedOnly || isExported(it);
        if (it instanceof Module) {
          if (shouldAdd) {
            if (kind.isTestsIncluded()) {
              classpath.add(getModuleOutput(module, true));
            }
            classpath.add(getModuleOutput(module, false));
          }
          collectCompileClasspath((Module)it, kind, classpath, processed, true);
        }
        else {
          if (shouldAdd) {
            for (String root : it.getClasspathRoots(kind)) {
              classpath.add(new File(root));
            }
          }
        }
      }
    }
  }

  private static boolean isExported(ClasspathItem it) {
    return true; // todo
  }

  private void collectRuntimeClasspath(Module module, ClasspathKind kind, Set<File> classpath, Set<Module> processed) {
    if (!processed.contains(module)) {
      processed.add(module);

      // IMPORTANT! assuming that ModuleSource order entry is included in module.getClassPath()
      for (ClasspathItem it : module.getClasspath(kind)) {
        if (it instanceof Module) {
          if (kind.isTestsIncluded()) {
            classpath.add(getModuleOutput(module, true));
          }
          classpath.add(getModuleOutput(module, false));
          collectRuntimeClasspath((Module)it, kind, classpath, processed);
        }
        else {
          for (String root : it.getClasspathRoots(kind)) {
            classpath.add(new File(root));
          }
        }
      }
    }
  }

  private File getModuleOutput(Module module, boolean forTests) {
    if (myProjectTargetDir != null) {
      final File basePath = new File(myProjectTargetDir, forTests ? "test" : "production");
      String name = module.getName();
      if (name.length() > 100) {
        name = name.substring(0, 100) + "_etc";
      }
      return new File(basePath, name);
    }

    return new File(forTests ? module.getTestOutputPath() : module.getOutputPath());
  }

}
