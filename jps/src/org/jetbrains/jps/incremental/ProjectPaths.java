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
  private final Map<ClasspathKind, Map<ModuleChunk, List<String>>> myCachedClasspath = new HashMap<ClasspathKind, Map<ModuleChunk, List<String>>>();

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

  public Collection<File> getBootstrapCompilationClasspath(ModuleChunk chunk, boolean includeTests, boolean excludeMainModuleOutput) {
    return getClasspathFiles(chunk, ClasspathKind.compile(includeTests), excludeMainModuleOutput, ClasspathPart.BEFORE_JDK);
  }

  public Collection<File> getCompilationClasspath(ModuleChunk chunk, boolean includeTests, boolean excludeMainModuleOutput) {
    return getClasspathFiles(chunk, ClasspathKind.compile(includeTests), excludeMainModuleOutput, ClasspathPart.AFTER_JDK);
  }

  private Collection<File> getClasspathFiles(ModuleChunk chunk, ClasspathKind kind, final boolean excludeMainModuleOutput, ClasspathPart classpathPart) {
    final Set<File> files = new LinkedHashSet<File>();
    final Set<Module> processedModules = new HashSet<Module>();
    for (Module module : chunk.getModules()) {
      ClasspathItemFilter filter = classpathPart == ClasspathPart.WHOLE ? ACCEPT_ALL :
                                   classpathPart == ClasspathPart.BEFORE_JDK ? new BeforeSdkItemFilter(module)
                                       : new NotFilter(new BeforeSdkItemFilter(module));
      collectClasspath(module, kind, files, processedModules, false, excludeMainModuleOutput, false, filter);
    }
    return files;
  }

  private void collectClasspath(Module module, ClasspathKind kind, Set<File> classpath, Set<Module> processed, boolean exportedOnly,
                                boolean excludeMainModuleOutput, final boolean excludeSdk, ClasspathItemFilter filter) {
    if (!processed.add(module)) return;

    for (ClasspathItem it : module.getClasspath(kind, exportedOnly)) {
      if (!filter.accept(module, it)
          || it instanceof Sdk && excludeSdk) continue;

      if (it instanceof ModuleSourceEntry) {
        final Module dep = ((ModuleSourceEntry) it).getModule();
        if (!excludeMainModuleOutput && kind.isTestsIncluded()) {
          classpath.add(getModuleOutputDir(dep, true));
        }
        if (!excludeMainModuleOutput || kind.isTestsIncluded()) {
          classpath.add(getModuleOutputDir(dep, false));
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
      if (item instanceof ModuleSourceEntry) {
        final Module dep = ((ModuleSourceEntry) item).getModule();
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

  private static interface ClasspathItemFilter {
    boolean accept(Module module, ClasspathItem item);
  }

  private static enum ClasspathPart {WHOLE, BEFORE_JDK, AFTER_JDK}

  private static final ClasspathItemFilter ACCEPT_ALL = new ClasspathItemFilter() {
    public boolean accept(Module module, ClasspathItem item) {
      return true;
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
}
