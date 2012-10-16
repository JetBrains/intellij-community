package org.jetbrains.jps;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.model.module.JpsSdkDependency;
import org.jetbrains.jps.util.JpsPathUtil;

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
  //private final Map<JpsJavaClasspathKind, Map<ModuleChunk, List<String>>> myCachedClasspath = new HashMap<JpsJavaClasspathKind, Map<ModuleChunk, List<String>>>();

  public ProjectPaths(@NotNull JpsProject project) {
    myProject = project;
  }

  public Collection<File> getCompilationClasspathFiles(ModuleChunk chunk,
                                                       boolean includeTests,
                                                       final boolean excludeMainModuleOutput,
                                                       final boolean exportedOnly) {
    return getClasspathFiles(chunk, JpsJavaClasspathKind.compile(includeTests), excludeMainModuleOutput, ClasspathPart.WHOLE, exportedOnly);
  }

  public Collection<File> getPlatformCompilationClasspath(ModuleChunk chunk, boolean excludeMainModuleOutput) {
    return getClasspathFiles(chunk, JpsJavaClasspathKind.compile(chunk.containsTests()), excludeMainModuleOutput, ClasspathPart.BEFORE_JDK, true);
  }

  public Collection<File> getCompilationClasspath(ModuleChunk chunk, boolean excludeMainModuleOutput) {
    return getClasspathFiles(chunk, JpsJavaClasspathKind.compile(chunk.containsTests()), excludeMainModuleOutput, ClasspathPart.AFTER_JDK, true);
  }

  private Collection<File> getClasspathFiles(ModuleChunk chunk,
                                             JpsJavaClasspathKind kind,
                                             final boolean excludeMainModuleOutput,
                                             ClasspathPart classpathPart, final boolean exportedOnly) {
    final Set<File> files = new LinkedHashSet<File>();
    for (JpsModule module : chunk.getModules()) {
      JpsJavaDependenciesEnumerator enumerator = JpsJavaExtensionService.dependencies(module).includedIn(kind).recursively();
      if (exportedOnly) {
        enumerator = enumerator.exportedOnly();
      }
      if (classpathPart == ClasspathPart.BEFORE_JDK) {
        enumerator = enumerator.satisfying(new BeforeJavaSdkItemFilter(module));
      }
      else if (classpathPart == ClasspathPart.AFTER_JDK) {
        enumerator = enumerator.satisfying(new AfterJavaSdkItemFilter(module));
      }
      JpsJavaDependenciesRootsEnumerator rootsEnumerator = enumerator.classes();
      if (excludeMainModuleOutput) {
        rootsEnumerator = rootsEnumerator.withoutSelfModuleOutput();
      }
      files.addAll(rootsEnumerator.getRoots());
    }

    if (classpathPart == ClasspathPart.BEFORE_JDK) {
      for (JpsModule module : chunk.getModules()) {
        JpsSdk<JpsDummyElement> sdk = module.getSdk(JpsJavaSdkType.INSTANCE);
        if (sdk != null) {
          files.addAll(sdk.getParent().getFiles(JpsOrderRootType.COMPILED));
        }
      }
    }
    return files;
  }

  private static void addFile(Set<File> classpath, @Nullable String url) {
    if (url != null) {
      classpath.add(JpsPathUtil.urlToFile(url));
    }
  }

  /**
   *
   * @param chunk
   * @return mapping "sourceRoot" -> "package prefix" Package prefix uses slashes instead of dots and ends with trailing slash
   */
  public static Map<File, String> getSourceRootsWithDependents(ModuleChunk chunk) {
    final boolean includeTests = chunk.containsTests();
    final Map<File, String> result = new LinkedHashMap<File, String>();
    processModulesRecursively(chunk, JpsJavaClasspathKind.compile(includeTests), new Consumer<JpsModule>() {
      @Override
      public void consume(JpsModule module) {
        for (JpsModuleSourceRoot root : module.getSourceRoots()) {
          if (root.getRootType().equals(JavaSourceRootType.SOURCE) ||
              includeTests && root.getRootType().equals(JavaSourceRootType.TEST_SOURCE)) {
            JavaSourceRootProperties properties = (JavaSourceRootProperties)((JpsSimpleElement<?>)root.getProperties()).getData();
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

  public static Collection<File> getOutputPathsWithDependents(final ModuleChunk chunk) {
    final boolean forTests = chunk.containsTests();
    final Set<File> sourcePaths = new LinkedHashSet<File>();
    processModulesRecursively(chunk, JpsJavaClasspathKind.compile(forTests), new Consumer<JpsModule>() {
      @Override
      public void consume(JpsModule module) {
        addFile(sourcePaths, JpsJavaExtensionService.getInstance().getOutputUrl(module, forTests));
      }
    });
    return sourcePaths;
  }

  public static Set<JpsModule> getModulesWithDependentsRecursively(final JpsModule module, final boolean includeTests) {
    return JpsJavaExtensionService.dependencies(module).includedIn(JpsJavaClasspathKind.compile(includeTests)).recursively().getModules();
  }

  private static void processModulesRecursively(ModuleChunk chunk, JpsJavaClasspathKind kind, Consumer<JpsModule> processor) {
    JpsJavaExtensionService.getInstance().enumerateDependencies(chunk.getModules()).includedIn(kind).recursively().processModules(processor);
  }

  @Nullable
  public File getModuleOutputDir(JpsModule module, boolean forTests) {
    return JpsJavaExtensionService.getInstance().getOutputDirectory(module, forTests);
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
    return new File(parentFile, DEFAULT_GENERATED_DIR_NAME);
  }

  private enum ClasspathPart {WHOLE, BEFORE_JDK, AFTER_JDK}

  private static class BeforeJavaSdkItemFilter implements Condition<JpsDependencyElement> {
    private JpsModule myModule;
    private boolean mySdkFound;

    private BeforeJavaSdkItemFilter(JpsModule module) {
      myModule = module;
    }

    @Override
    public boolean value(JpsDependencyElement dependency) {
      boolean isJavaSdk = dependency instanceof JpsSdkDependency && ((JpsSdkDependency)dependency).getSdkType().equals(JpsJavaSdkType.INSTANCE);
      if (myModule.equals(dependency.getContainingModule()) && isJavaSdk) {
        mySdkFound = true;
      }
      return !mySdkFound && !isJavaSdk;
    }
  }

  private static class AfterJavaSdkItemFilter implements Condition<JpsDependencyElement> {
    private JpsModule myModule;
    private boolean mySdkFound;

    private AfterJavaSdkItemFilter(JpsModule module) {
      myModule = module;
    }

    @Override
    public boolean value(JpsDependencyElement dependency) {
      if (myModule.equals(dependency.getContainingModule()) &&
          dependency instanceof JpsSdkDependency && ((JpsSdkDependency)dependency).getSdkType().equals(JpsJavaSdkType.INSTANCE)) {
        mySdkFound = true;
        return false;
      }
      return mySdkFound;
    }
  }

}
