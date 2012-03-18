package org.jetbrains.jps;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  //private final Map<ClasspathKind, Map<ModuleChunk, List<String>>> myCachedClasspath = new HashMap<ClasspathKind, Map<ModuleChunk, List<String>>>();

  public ProjectPaths(Project project) {
    this(project, null);
  }

  public ProjectPaths(Project project, @Nullable File projectTargetDir) {
    myProject = project;
    myProjectTargetDir = projectTargetDir;
  }

  public Collection<File> getClasspathFiles(Module module, ClasspathKind kind) {
    final Set<File> files = new LinkedHashSet<File>();
    collectClasspath(module, kind, files, new HashSet<Module>(), false, !kind.isRuntime(), false, ACCEPT_ALL);
    return files;
  }

  public Collection<File> getClasspathFiles(ModuleChunk chunk, ClasspathKind kind) {
    return getClasspathFiles(chunk, kind, !kind.isRuntime());
  }

  public List<String> getClasspath(ModuleChunk chunk, ClasspathKind kind) {
    return getPathsList(getClasspathFiles(chunk, kind));
  }

  public Collection<File> getClasspathFiles(ModuleChunk chunk, ClasspathKind kind, final boolean excludeMainModuleOutput) {
    return getClasspathFiles(chunk, kind, excludeMainModuleOutput, ClasspathPart.WHOLE);
  }

  public Collection<File> getPlatformCompilationClasspath(ModuleChunk chunk, boolean includeTests, boolean excludeMainModuleOutput) {
    return getClasspathFiles(chunk, ClasspathKind.compile(includeTests), excludeMainModuleOutput, ClasspathPart.BEFORE_JDK);
  }

  public Collection<File> getCompilationClasspath(ModuleChunk chunk, boolean includeTests, boolean excludeMainModuleOutput) {
    return getClasspathFiles(chunk, ClasspathKind.compile(includeTests), excludeMainModuleOutput, ClasspathPart.AFTER_JDK);
  }

  private Collection<File> getClasspathFiles(ModuleChunk chunk, ClasspathKind kind, final boolean excludeMainModuleOutput, ClasspathPart classpathPart) {
    final Set<File> files = new LinkedHashSet<File>();
    for (Module module : chunk.getModules()) {
      final ClasspathItemFilter filter = classpathPart == ClasspathPart.WHOLE ? ACCEPT_ALL :
                                         classpathPart == ClasspathPart.BEFORE_JDK ? new BeforeSdkItemFilter(module) : new NotFilter(new BeforeSdkItemFilter(module));
      collectClasspath(module, kind, files, new HashSet<Module>(), false, excludeMainModuleOutput, false, filter);
    }
    return files;
  }

  private void collectClasspath(Module module, ClasspathKind kind, Set<File> classpath, Set<Module> processed, boolean exportedOnly,
                                boolean excludeMainModuleOutput, final boolean excludeSdk, ClasspathItemFilter filter) {
    if (!processed.add(module)) {
      return;
    }

    for (ClasspathItem it : module.getClasspath(kind, exportedOnly)) {
      if (!filter.accept(module, it) || it instanceof Sdk && excludeSdk) {
        continue;
      }

      if (it instanceof ModuleSourceEntry) {
        final Module dep = ((ModuleSourceEntry) it).getModule();
        if (!excludeMainModuleOutput && kind.isTestsIncluded()) {
          final File out = getModuleOutputDir(dep, true);
          if (out != null) {
            classpath.add(out);
          }
        }
        if (!excludeMainModuleOutput || kind.isTestsIncluded()) {
          final File out = getModuleOutputDir(dep, false);
          if (out != null) {
            classpath.add(out);
          }
        }
      }
      else if (it instanceof Module) {
        collectClasspath((Module) it, kind, classpath, processed, !kind.isRuntime(), false, true, filter);
      }
      else {
        addFiles(classpath, it.getClasspathRoots(kind));
      }
    }
  }

  private static void addFiles(Set<File> files, final Collection<String> paths) {
    for (String root : paths) {
      files.add(new File(root));
    }
  }

  public static List<String> getPathsList(Collection<File> files) {
    final List<String> result = new ArrayList<String>();
    for (File file : files) {
      result.add(getCanonicalPath(file));
    }
    return result;
  }

  public static Collection<File> getSourcePathsWithDependents(ModuleChunk chunk, boolean includeTests) {
    final Set<File> sourcePaths = new LinkedHashSet<File>();
    collectPathsRecursively(chunk, ClasspathKind.compile(includeTests), new PathsGetter() {
      public void apply(Module module, ClasspathKind kind) {
        addFiles(sourcePaths, module.getSourceRoots());
        if (kind.isTestsIncluded()) {
          addFiles(sourcePaths, module.getTestRoots());
        }
      }
    });
    return sourcePaths;
  }

  /**
   * @param chunk
   * @param includeTests
   * @return mapping "sourceRoot" -> "package prefix" Package prefix uses slashes instead of dots and ends with trailing slash
   */
  public static Map<File, String> getSourceRootsWithDependents(ModuleChunk chunk, boolean includeTests) {
    final Map<File, String> result = new LinkedHashMap<File, String>();
    collectPathsRecursively(chunk, ClasspathKind.compile(includeTests), new PathsGetter() {
      public void apply(Module module, ClasspathKind kind) {
        final Map<String, String> prefixes = module.getSourceRootPrefixes();
        for (String root : module.getSourceRoots()) {
          addRoot(prefixes, root);
        }
        if (kind.isTestsIncluded()) {
          for (String root : module.getTestRoots()) {
            addRoot(prefixes, root);
          }
        }
      }

      private void addRoot(Map<String, String> prefixes, String root) {
        String prefix = prefixes.get(root);
        if (prefix != null) {
          if (!prefix.isEmpty()) {
            prefix = prefix.replace('.', '/');
            if (!prefix.endsWith("/")) {
              prefix += "/";
            }
          }
          else {
            prefix = null;
          }
        }
        result.put(new File(root), prefix);
      }
    });
    return result;
  }

  public static Collection<File> getOutputPathsWithDependents(final ModuleChunk chunk, final boolean forTests) {
    final Set<File> sourcePaths = new LinkedHashSet<File>();
    collectPathsRecursively(chunk, ClasspathKind.compile(forTests), new PathsGetter() {
      public void apply(Module module, ClasspathKind kind) {
        if (forTests) {
          addFiles(sourcePaths, Collections.singleton(module.getTestOutputPath()));
        }
        else {
          addFiles(sourcePaths, Collections.singleton(module.getOutputPath()));
        }
      }
    });
    return sourcePaths;
  }

  public static Set<Module> getModulesWithDependentsRecursively(final ModuleChunk chunk, final boolean includeTests) {
    final Set<Module> result = new HashSet<Module>();
    collectPathsRecursively(chunk, ClasspathKind.compile(includeTests), new PathsGetter() {
      public void apply(Module module, ClasspathKind kind) {
        result.add(module);
      }
    });
    return result;
  }

  private interface PathsGetter {
    void apply(Module module, ClasspathKind kind);
  }

  private static void collectPathsRecursively(ModuleChunk chunk, ClasspathKind kind, PathsGetter proc) {
    final HashSet<Module> processed = new HashSet<Module>();
    for (Module module : chunk.getModules()) {
      collectPathsRecursively(module, kind, processed, proc);
    }
  }

  private static void collectPathsRecursively(Module module, ClasspathKind kind, Set<Module> processed, PathsGetter processor) {
    if (processed.add(module)) {
      for (ClasspathItem item : module.getClasspath(kind, false)) {
        if (item instanceof ModuleSourceEntry) {
          final Module dep = ((ModuleSourceEntry) item).getModule();
          processor.apply(dep, kind);
        }
        else if (item instanceof Module) {
          collectPathsRecursively((Module)item, kind, processed, processor);
        }
      }
    }
  }

  public void setCustomModuleOutputDir(Module module, boolean forTests, File outputDir) {
    (forTests ? myCustomModuleTestOutputDir : myCustomModuleOutputDir).put(module, outputDir);
  }

  @Nullable
  public File getModuleOutputDir(Module module, boolean forTests) {
    File customOutput = (forTests ? myCustomModuleTestOutputDir : myCustomModuleOutputDir).get(module);
    if (customOutput != null) {
      return customOutput;
    }

    if (myProjectTargetDir != null) {
      final File basePath = new File(myProjectTargetDir, forTests ? "test" : "production");
      return new File(basePath, module.getName());
    }

    final String path = forTests ? module.getTestOutputPath() : module.getOutputPath();
    return path != null ? new File(path) : null;
  }

  public List<String> getProjectRuntimeClasspath(boolean includeTests) {
    Set<File> classpath = new LinkedHashSet<File>();
    final ClasspathKind kind = ClasspathKind.runtime(includeTests);
    for (Module module : myProject.getModules().values()) {
      collectClasspath(module, kind, classpath, new HashSet<Module>(), false, false, false, WITHOUT_DEP_MODULES);
    }
    return getPathsList(classpath);
  }

  private interface ClasspathItemFilter {
    boolean accept(Module module, ClasspathItem item);
  }

  private static enum ClasspathPart {WHOLE, BEFORE_JDK, AFTER_JDK}

  private static final ClasspathItemFilter ACCEPT_ALL = new ClasspathItemFilter() {
    public boolean accept(Module module, ClasspathItem item) {
      return true;
    }
  };

  private static final ClasspathItemFilter WITHOUT_DEP_MODULES = new ClasspathItemFilter() {
    public boolean accept(Module module, ClasspathItem item) {
      return !(item instanceof Module);
    }
  };

  private static class BeforeSdkItemFilter implements ClasspathItemFilter {
    private Module myModule;
    private boolean mySdkFound;

    private BeforeSdkItemFilter(Module module) {
      myModule = module;
    }

    public boolean accept(Module module, ClasspathItem item) {
      if (myModule.equals(module) && item instanceof Sdk) {
        mySdkFound = true;
        return true;
      }
      return !mySdkFound && !(item instanceof Sdk);
    }
  }

  private static class NotFilter implements ClasspathItemFilter {
    private ClasspathItemFilter myFilter;

    private NotFilter(ClasspathItemFilter filter) {
      myFilter = filter;
    }

    public boolean accept(Module module, ClasspathItem item) {
      return !myFilter.accept(module, item);
    }
  }

  private static String getCanonicalPath(File file) {
    final String path = file.getPath();
    return path.contains(".")? FileUtil.toCanonicalPath(path) : FileUtil.toSystemIndependentName(path);
  }

}
