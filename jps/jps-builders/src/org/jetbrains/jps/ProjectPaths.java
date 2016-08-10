/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.java.compiler.ProcessorConfigProfile;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.*;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/30/11
 */
public class ProjectPaths {
  private ProjectPaths() {
  }

  @NotNull
  public static Collection<File> getCompilationClasspathFiles(ModuleChunk chunk,
                                                       boolean includeTests,
                                                       final boolean excludeMainModuleOutput,
                                                       final boolean exportedOnly) {
    return getClasspathFiles(chunk, JpsJavaClasspathKind.compile(includeTests), excludeMainModuleOutput, ClasspathPart.WHOLE, exportedOnly);
  }

  @NotNull
  public static Collection<File> getPlatformCompilationClasspath(ModuleChunk chunk, boolean excludeMainModuleOutput) {
    return getClasspathFiles(chunk, JpsJavaClasspathKind.compile(chunk.containsTests()), excludeMainModuleOutput, ClasspathPart.BEFORE_JDK, true);
  }

  @NotNull
  public static Collection<File> getCompilationClasspath(ModuleChunk chunk, boolean excludeMainModuleOutput) {
    return getClasspathFiles(chunk, JpsJavaClasspathKind.compile(chunk.containsTests()), excludeMainModuleOutput, ClasspathPart.AFTER_JDK, true);
  }

  // todo: implementation can be changed
  @NotNull
  public static Collection<File> getCompilationModulePath(ModuleChunk chunk) {
    return getClasspathFiles(chunk, JpsJavaClasspathKind.compile(chunk.containsTests()), true, ClasspathPart.MODULE_PATH, true);
  }

  @NotNull
  private static Collection<File> getClasspathFiles(ModuleChunk chunk,
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
      else if (classpathPart == ClasspathPart.MODULE_PATH) {
        enumerator = enumerator.satisfying(new ModuleSourceElementsFilter());
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
  @NotNull
  public static Map<File, String> getSourceRootsWithDependents(ModuleChunk chunk) {
    final boolean includeTests = chunk.containsTests();
    final Map<File, String> result = new LinkedHashMap<File, String>();
    processModulesRecursively(chunk, JpsJavaClasspathKind.compile(includeTests), new Consumer<JpsModule>() {
      @Override
      public void consume(JpsModule module) {
        for (JpsModuleSourceRoot root : module.getSourceRoots()) {
          if (root.getRootType().equals(JavaSourceRootType.SOURCE) ||
              includeTests && root.getRootType().equals(JavaSourceRootType.TEST_SOURCE)) {
            String prefix = ((JavaSourceRootProperties)root.getProperties()).getPackagePrefix();
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
  public static File getModuleOutputDir(JpsModule module, boolean forTests) {
    return JpsJavaExtensionService.getInstance().getOutputDirectory(module, forTests);
  }

  @Nullable
  public static File getAnnotationProcessorGeneratedSourcesOutputDir(JpsModule module, final boolean forTests, ProcessorConfigProfile profile) {
    final String sourceDirName = profile.getGeneratedSourcesDirectoryName(forTests);
    if (profile.isOutputRelativeToContentRoot()) {
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
      final File parent = JpsPathUtil.urlToFile(roots.get(0));
      return StringUtil.isEmpty(sourceDirName)? parent : new File(parent, sourceDirName);
    }

    final File outputDir = getModuleOutputDir(module, forTests);
    if (outputDir == null) {
      return null;
    }
    return StringUtil.isEmpty(sourceDirName)? outputDir : new File(outputDir, sourceDirName);
  }

  private enum ClasspathPart {WHOLE, BEFORE_JDK, AFTER_JDK, MODULE_PATH}

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
      if (myModule.equals(dependency.getContainingModule())) {
        if (dependency instanceof JpsSdkDependency && ((JpsSdkDependency)dependency).getSdkType().equals(JpsJavaSdkType.INSTANCE)) {
          mySdkFound = true;
          return false;
        }
      }
      return mySdkFound;
    }
  }

  private static class ModuleSourceElementsFilter implements Condition<JpsDependencyElement> {

    private ModuleSourceElementsFilter() {
    }

    @Override
    public boolean value(JpsDependencyElement dependency) {
      return dependency instanceof JpsModuleDependency || dependency instanceof JpsModuleSourceDependency;
    }
  }

}
