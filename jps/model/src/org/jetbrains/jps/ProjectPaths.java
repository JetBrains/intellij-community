package org.jetbrains.jps;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaClasspathKind;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryRoot;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.module.*;

import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/30/11
 */
public class ProjectPaths {
  private static final String DEFAULT_GENERATED_DIR_NAME = "generated";
  @NotNull
  private final JpsProject myProject;
  @Nullable
  private final File myProjectTargetDir;
  //private final Map<JpsJavaClasspathKind, Map<ModuleChunk, List<String>>> myCachedClasspath = new HashMap<JpsJavaClasspathKind, Map<ModuleChunk, List<String>>>();

  public ProjectPaths(JpsProject project) {
    this(project, null);
  }

  public ProjectPaths(JpsProject project, @Nullable File projectTargetDir) {
    myProject = project;
    myProjectTargetDir = projectTargetDir;
  }

  public Collection<File> getClasspathFiles(JpsModule module, JpsJavaClasspathKind kind) {
    final Set<File> files = new LinkedHashSet<File>();
    collectClasspath(module, kind, files, new HashSet<JpsModule>(), false, !kind.isRuntime(), false, ACCEPT_ALL);
    return files;
  }

  public Collection<File> getClasspathFiles(ModuleChunk chunk, JpsJavaClasspathKind kind) {
    return getClasspathFiles(chunk, kind, !kind.isRuntime());
  }

  public List<String> getClasspath(ModuleChunk chunk, JpsJavaClasspathKind kind) {
    return getPathsList(getClasspathFiles(chunk, kind));
  }

  public Collection<File> getClasspathFiles(ModuleChunk chunk, JpsJavaClasspathKind kind, final boolean excludeMainModuleOutput) {
    return getClasspathFiles(chunk, kind, excludeMainModuleOutput, ClasspathPart.WHOLE);
  }

  public Collection<File> getPlatformCompilationClasspath(ModuleChunk chunk, boolean includeTests, boolean excludeMainModuleOutput) {
    return getClasspathFiles(chunk, JpsJavaClasspathKind.compile(includeTests), excludeMainModuleOutput, ClasspathPart.BEFORE_JDK);
  }

  public Collection<File> getCompilationClasspath(ModuleChunk chunk, boolean includeTests, boolean excludeMainModuleOutput) {
    return getClasspathFiles(chunk, JpsJavaClasspathKind.compile(includeTests), excludeMainModuleOutput, ClasspathPart.AFTER_JDK);
  }

  private Collection<File> getClasspathFiles(ModuleChunk chunk, JpsJavaClasspathKind kind, final boolean excludeMainModuleOutput, ClasspathPart classpathPart) {
    final Set<File> files = new LinkedHashSet<File>();
    for (JpsModule module : chunk.getModules()) {
      final ClasspathItemFilter filter = classpathPart == ClasspathPart.WHOLE ? ACCEPT_ALL :
                                         classpathPart == ClasspathPart.BEFORE_JDK ? new BeforeSdkItemFilter(module) : new NotFilter(new BeforeSdkItemFilter(module));
      collectClasspath(module, kind, files, new HashSet<JpsModule>(), false, excludeMainModuleOutput, false, filter);
    }
    return files;
  }

  private void collectClasspath(JpsModule module, JpsJavaClasspathKind kind, Set<File> classpath, Set<JpsModule> processed, boolean exportedOnly,
                                boolean excludeMainModuleOutput, final boolean excludeSdk, ClasspathItemFilter filter) {
    if (!processed.add(module)) {
      return;
    }

    for (JpsDependencyElement it : JpsJavaExtensionService.getInstance().getDependencies(module, kind, exportedOnly)) {
      if (!filter.accept(module, it) || it instanceof JpsSdkDependency && excludeSdk) {
        continue;
      }

      if (it instanceof JpsModuleSourceDependency) {
        if (!excludeMainModuleOutput && kind.isTestsIncluded()) {
          final File out = getModuleOutputDir(module, true);
          if (out != null) {
            classpath.add(out);
          }
        }
        if (!excludeMainModuleOutput || kind.isTestsIncluded()) {
          final File out = getModuleOutputDir(module, false);
          if (out != null) {
            classpath.add(out);
          }
        }
      }
      else if (it instanceof JpsModuleDependency) {
        final JpsModule dep = ((JpsModuleDependency)it).getModule();
        if (dep != null) {
          collectClasspath(dep, kind, classpath, processed, !kind.isRuntime(), false, true, filter);
        }
      }
      else if (it instanceof JpsLibraryDependency) {
        addLibraryFiles(classpath, ((JpsLibraryDependency)it).getLibrary());
      }
      else if (it instanceof JpsSdkDependency) {
        addLibraryFiles(classpath, ((JpsSdkDependency)it).resolveSdk());
      }
    }
  }

  public static void addLibraryFiles(Collection<File> classpath, @Nullable JpsLibrary library) {
    if (library != null) {
      for (JpsLibraryRoot root : library.getRoots(JpsOrderRootType.COMPILED)) {
        final File file = JpsPathUtil.urlToFile(root.getUrl());
        switch (root.getInclusionOptions()) {
          case ROOT_ITSELF:
            classpath.add(file);
            break;
          case ARCHIVES_UNDER_ROOT:
            collectArchives(file, false, classpath);
            break;
          case ARCHIVES_UNDER_ROOT_RECURSIVELY:
            collectArchives(file, true, classpath);
            break;
        }
      }
    }
  }

  private static void collectArchives(File file, boolean recursively, Collection<File> result) {
    final File[] children = file.listFiles();
    if (children != null) {
      for (File child : children) {
        final String extension = FileUtil.getExtension(child.getName());
        if (child.isDirectory()) {
          if (recursively) {
            collectArchives(child, recursively, result);
          }
        }
        else if (extension.equals("jar") || extension.equals("zip")) {
          result.add(child);
        }
      }
    }
  }

  private static void addFile(Set<File> classpath, @Nullable String url) {
    if (url != null) {
      classpath.add(JpsPathUtil.urlToFile(url));
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
    collectPathsRecursively(chunk, JpsJavaClasspathKind.compile(includeTests), new PathsGetter() {
      public void apply(JpsModule module, JpsJavaClasspathKind kind) {
        for (JpsModuleSourceRoot root : module.getSourceRoots()) {
          if (root.getRootType().equals(JavaSourceRootType.SOURCE) ||
              kind.isTestsIncluded() && root.getRootType().equals(JavaSourceRootType.TEST_SOURCE)) {
            addFile(sourcePaths, root.getUrl());
          }
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
    collectPathsRecursively(chunk, JpsJavaClasspathKind.compile(includeTests), new PathsGetter() {
      public void apply(JpsModule module, JpsJavaClasspathKind kind) {
        for (JpsModuleSourceRoot root : module.getSourceRoots()) {
          if (root.getRootType().equals(JavaSourceRootType.SOURCE) ||
              kind.isTestsIncluded() && root.getRootType().equals(JavaSourceRootType.TEST_SOURCE)) {
            JavaSourceRootProperties properties = (JavaSourceRootProperties)root.getProperties();
            String prefix = properties.getPackagePrefix();
            if (!prefix.isEmpty()) {
              prefix = prefix.replace('.', '/');
              if (!prefix.endsWith("/")) {
                prefix += "/";
              }
            }
            else {
              prefix = null;
            }
            result.put(JpsPathUtil.urlToFile(root.getUrl()), prefix);
          }
        }
      }
    });
    return result;
  }

  public static Collection<File> getOutputPathsWithDependents(final ModuleChunk chunk, final boolean forTests) {
    final Set<File> sourcePaths = new LinkedHashSet<File>();
    collectPathsRecursively(chunk, JpsJavaClasspathKind.compile(forTests), new PathsGetter() {
      public void apply(JpsModule module, JpsJavaClasspathKind kind) {
        addFile(sourcePaths, JpsJavaExtensionService.getInstance().getOutputUrl(module, forTests));
      }
    });
    return sourcePaths;
  }

  public static Set<JpsModule> getModulesWithDependentsRecursively(final JpsModule module, final boolean includeTests) {
    final Set<JpsModule> result = new HashSet<JpsModule>();
    collectPathsRecursively(module, JpsJavaClasspathKind.compile(includeTests), new HashSet<JpsModule>(), new PathsGetter() {
      public void apply(JpsModule module, JpsJavaClasspathKind kind) {
        result.add(module);
      }
    });
    return result;
  }

  private interface PathsGetter {
    void apply(JpsModule module, JpsJavaClasspathKind kind);
  }

  private static void collectPathsRecursively(ModuleChunk chunk, JpsJavaClasspathKind kind, PathsGetter proc) {
    final HashSet<JpsModule> processed = new HashSet<JpsModule>();
    for (JpsModule module : chunk.getModules()) {
      collectPathsRecursively(module, kind, processed, proc);
    }
  }

  private static void collectPathsRecursively(JpsModule module, JpsJavaClasspathKind kind, Set<JpsModule> processed, PathsGetter processor) {
    if (processed.add(module)) {
      for (JpsDependencyElement item : JpsJavaExtensionService.getInstance().getDependencies(module, kind, false)) {
        if (item instanceof JpsModuleSourceDependency) {
          processor.apply(module, kind);
        }
        else if (item instanceof JpsModuleDependency) {
          final JpsModule dep = ((JpsModuleDependency)item).getModule();
          if (dep != null) {
            collectPathsRecursively(dep, kind, processed, processor);
          }
        }
      }
    }
  }

  @Nullable
  public File getModuleOutputDir(JpsModule module, boolean forTests) {
    if (myProjectTargetDir != null) {
      final File basePath = new File(myProjectTargetDir, forTests ? "test" : "production");
      return new File(basePath, module.getName());
    }

    final String url = JpsJavaExtensionService.getInstance().getOutputUrl(module, forTests);
    return url != null ? JpsPathUtil.urlToFile(url) : null;
  }

  @Nullable
  public File getAnnotationProcessorGeneratedSourcesOutputDir(JpsModule module, final boolean forTests, String sourceDirName) {
    if (!StringUtil.isEmpty(sourceDirName)) {
      List<String> roots = module.getContentRootsList().getUrls();
      if (roots.isEmpty()) {
        return null;
      }
      if (roots.size() > 1) {
        roots = new ArrayList<String>(roots); // sort roots to get deterministic result
        Collections.sort(roots, new Comparator<String>() {
          @Override
          public int compare(String o1, String o2) {
            return o1.compareTo(o2);
          }
        });
      }
      return new File(JpsPathUtil.urlToFile(roots.get(0)), sourceDirName);
    }

    final File outputDir = getModuleOutputDir(module, forTests);
    if (outputDir == null) {
      return null;
    }
    final File parentFile = outputDir.getParentFile();
    if (parentFile == null) {
      return null;
    }
    return new File(parentFile, outputDir.getName() + "_" + DEFAULT_GENERATED_DIR_NAME);
  }

  public List<String> getProjectRuntimeClasspath(boolean includeTests) {
    Set<File> classpath = new LinkedHashSet<File>();
    final JpsJavaClasspathKind kind = JpsJavaClasspathKind.runtime(includeTests);
    for (JpsModule module : myProject.getModules()) {
      collectClasspath(module, kind, classpath, new HashSet<JpsModule>(), false, false, false, WITHOUT_DEP_MODULES);
    }
    return getPathsList(classpath);
  }

  private interface ClasspathItemFilter {
    boolean accept(JpsModule module, JpsDependencyElement item);
  }

  private enum ClasspathPart {WHOLE, BEFORE_JDK, AFTER_JDK}

  private static final ClasspathItemFilter ACCEPT_ALL = new ClasspathItemFilter() {
    public boolean accept(JpsModule module, JpsDependencyElement item) {
      return true;
    }
  };

  private static final ClasspathItemFilter WITHOUT_DEP_MODULES = new ClasspathItemFilter() {
    public boolean accept(JpsModule module, JpsDependencyElement item) {
      return !(item instanceof JpsModuleDependency);
    }
  };

  private static class BeforeSdkItemFilter implements ClasspathItemFilter {
    private JpsModule myModule;
    private boolean mySdkFound;

    private BeforeSdkItemFilter(JpsModule module) {
      myModule = module;
    }

    public boolean accept(JpsModule module, JpsDependencyElement item) {
      if (myModule.equals(module) && item instanceof JpsSdkDependency) {
        mySdkFound = true;
        return true;
      }
      return !mySdkFound && !(item instanceof JpsSdkDependency);
    }
  }

  private static class NotFilter implements ClasspathItemFilter {
    private ClasspathItemFilter myFilter;

    private NotFilter(ClasspathItemFilter filter) {
      myFilter = filter;
    }

    public boolean accept(JpsModule module, JpsDependencyElement item) {
      return !myFilter.accept(module, item);
    }
  }

  private static String getCanonicalPath(File file) {
    final String path = file.getPath();
    return path.contains(".")? FileUtil.toCanonicalPath(path) : FileUtil.toSystemIndependentName(path);
  }

}
