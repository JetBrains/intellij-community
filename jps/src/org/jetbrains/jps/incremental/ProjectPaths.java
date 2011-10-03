package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.*;

import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/30/11
 */
public class ProjectPaths {
  @NotNull
  private final Project myProject;
  @Nullable
  private final File myProjectTargetDir;
  private final Map<Module, File> myCustomModuleOutputDir = new HashMap<Module, File>();
  private final Map<Module, File> myCustomModuleTestOutputDir = new HashMap<Module, File>();

  public ProjectPaths(Project project) {
    this(project, null);
  }

  public ProjectPaths(Project project, @Nullable File projectTargetDir) {
    myProject = project;
    myProjectTargetDir = projectTargetDir;
  }

  public Collection<File> getClasspathFiles(Module module, ClasspathKind kind) {
    final Set<File> files = new LinkedHashSet<File>();
    collectClasspath(module, kind, files, new HashSet<Module>(), false, !kind.isRuntime());
    return files;
  }

  public Collection<File> getClasspathFiles(ModuleChunk chunk, ClasspathKind kind) {
    return getClasspathFiles(chunk, kind, !kind.isRuntime());
  }

  public List<String> getClasspath(ModuleChunk chunk, ClasspathKind kind) {
    return getPathsList(getClasspathFiles(chunk, kind));
  }

  public Collection<File> getClasspathFiles(ModuleChunk chunk, ClasspathKind kind, final boolean excludeMainModuleOutput) {
    final Set<File> files = new LinkedHashSet<File>();
    final Set<Module> processedModules = new HashSet<Module>();
    for (Module module : chunk.getModules()) {
      collectClasspath(module, kind, files, processedModules, false, excludeMainModuleOutput);
    }
    return files;
  }

  private void collectClasspath(Module module, ClasspathKind kind, Set<File> classpath, Set<Module> processed, boolean exportedOnly, boolean excludeMainModuleOutput) {
    if (!processed.add(module)) return;

    for (ClasspathItem it : module.getClasspath(kind, exportedOnly)) {
      if (it instanceof Module.ModuleSourceEntry) {
        final Module dep = ((Module.ModuleSourceEntry) it).getModule();
        if (!excludeMainModuleOutput && kind.isTestsIncluded()) {
          classpath.add(getModuleOutputDir(dep, true));
        }
        if (!excludeMainModuleOutput || kind.isTestsIncluded()) {
          classpath.add(getModuleOutputDir(dep, false));
        }
      }
      else if (it instanceof Module) {
        collectClasspath((Module) it, kind, classpath, processed, !kind.isRuntime(), false);
      }
      else {
        addFiles(classpath, it.getClasspathRoots(kind));
      }
    }
  }

  private void addFiles(Set<File> files, final Collection<String> paths) {
    for (String root : paths) {
      files.add(new File(root));
    }
  }

  public List<String> getSourcePathsForModuleWithDependents(ModuleChunk chunk, boolean includeTests) {
    Set<File> sourcePaths = new LinkedHashSet<File>();
    final HashSet<Module> processed = new HashSet<Module>();
    for (Module module : chunk.getModules()) {
      collectSourcePaths(module, ClasspathKind.compile(includeTests), sourcePaths, processed);
    }
    return getPathsList(sourcePaths);
  }

  public static List<String> getPathsList(Collection<File> files) {
    final List<String> result = new ArrayList<String>();
    for (File file : files) {
      result.add(file.getAbsolutePath());
    }
    return result;
  }

  private void collectSourcePaths(Module module, ClasspathKind kind, Set<File> sourcePaths, Set<Module> processed) {
    if (!processed.add(module)) return;

    for (ClasspathItem item : module.getClasspath(kind, false)) {
      if (item instanceof Module.ModuleSourceEntry) {
        final Module dep = ((Module.ModuleSourceEntry) item).getModule();
        addFiles(sourcePaths, dep.getSourceRoots());
        if (kind.isTestsIncluded()) {
          addFiles(sourcePaths, dep.getTestRoots());
        }
      }
      else if (item instanceof Module) {
        collectSourcePaths(module, kind, sourcePaths, processed);
      }
    }
  }

  public void setCustomModuleOutputDir(Module module, boolean forTests, File outputDir) {
    (forTests ? myCustomModuleTestOutputDir : myCustomModuleOutputDir).put(module, outputDir);
  }

  public File getModuleOutputDir(Module module, boolean forTests) {
    File customOutput = (forTests ? myCustomModuleTestOutputDir : myCustomModuleOutputDir).get(module);
    if (customOutput != null) {
      return customOutput;
    }

    if (myProjectTargetDir != null) {
      final File basePath = new File(myProjectTargetDir, forTests ? "test" : "production");
      return new File(basePath, module.getName());
    }

    return new File(forTests ? module.getTestOutputPath() : module.getOutputPath());
  }

}
