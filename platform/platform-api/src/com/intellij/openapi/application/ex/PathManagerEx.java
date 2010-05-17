/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 19, 2002
 * Time: 8:21:52 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.application.ex;

import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.TestRunnerUtil;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.containers.ConcurrentHashSet;
import junit.framework.TestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static java.util.Arrays.asList;

public class PathManagerEx {

  /**
   * All IDEA project files may be logically divided by the following criteria:
   * <ul>
   *   <li>files that are contained at <code>'community'</code> directory;</li>
   *   <li>all other files;</li>
   * </ul>
   * <p/>
   * File location types implied by criteria mentioned above are enumerated here.
   */
  private enum FileSystemLocation {
    ULTIMATE, COMMUNITY
  }

  /** Caches test data lookup strategy by class. */
  private static final ConcurrentMap<Class, TestDataLookupStrategy> CLASS_STRATEGY_CACHE
    = new ConcurrentHashMap<Class, TestDataLookupStrategy>();

  /**
   * Caches processed files.
   * <p/>
   * There is a possible case that particular file is processed but its test data lookup strategy is still not defined.
   * <p/>
   * We don't want to repeat the processing then during further requests for the same file, hence, we rememver paths of all
   * processed files here.
   */
  private static final Set<String> PROCESSED_FILES = new ConcurrentHashSet<String>();

  private PathManagerEx() {
  }

  /**
   * Enumerates possible strategies of test data lookup.
   * <p/>
   * Check member-level javadoc for more details.
   */
  public enum TestDataLookupStrategy {
    /**
     * Stands for algorithm that retrieves <code>'test data'</code> stored at the <code>'ultimate'</code> project level assuming
     * that it's used from the test running in context of <code>'ultimate'</code> project as well.
     * <p/>
     * Is assumed to be default strategy for all <code>'ultimate'</code> tests.
     */
    ULTIMATE,

    /**
     * Stands for algorithm that retrieves <code>'test data'</code> stored at the <code>'community'</code> project level assuming
     * that it's used from the test running in context of <code>'community'</code> project as well.
     * <p/>
     * Is assumed to be default strategy for all <code>'community'</code> tests.
     */
    COMMUNITY,

    /**
     * Stands for algorithm that retrieves <code>'test data'</code> stored at the <code>'community'</code> project level assuming
     * that it's used from the test running in context of <code>'ultimate'</code> project.
     */
    COMMUNITY_FROM_ULTIMATE
  }

  /**
   * It's assumed that test data location for both <code>community</code> and <code>ultimate</code> tests follows the same template:
   * <code>'<IDEA_HOME>/<RELATIVE_PATH>'</code>.
   * <p/>
   * <code>'IDEA_HOME'</code> here stands for path to IDEA installation; <code>'RELATIVE_PATH'</code> defines a path to
   * test data relative to IDEA installation path. That relative path may be different for <code>community</code>
   * and <code>ultimate</code> tests.
   * <p/>
   * This collection contains mappings from test group type to relative paths to use, i.e. it's possible to define more than one
   * relative path for the single test group. It's assumed that path definition algorithm iterates them and checks if
   * resulting absolute path points to existing directory. The one is returned in case of success; last path is returned otherwise.
   * <p/>
   * Hence, the order of relative paths for the single test group matters.
   */
  private static final Map<TestDataLookupStrategy, List<String>> TEST_DATA_RELATIVE_PATHS
    = new EnumMap<TestDataLookupStrategy, List<String>>(TestDataLookupStrategy.class);

  static {
    TEST_DATA_RELATIVE_PATHS.put(TestDataLookupStrategy.ULTIMATE, Collections.singletonList(toSystemDependentName("testData")));
    TEST_DATA_RELATIVE_PATHS.put(
      TestDataLookupStrategy.COMMUNITY, Collections.singletonList(toSystemDependentName("java/java-tests/testData"))
    );
    TEST_DATA_RELATIVE_PATHS.put(
      TestDataLookupStrategy.COMMUNITY_FROM_ULTIMATE, Collections.singletonList(toSystemDependentName("community/java/java-tests/testData"))
    );
  }

  /**
   * Shorthand for calling {@link #getTestDataPath(TestDataLookupStrategy)} with
   * {@link #guessTestDataLookupStrategy() guessed} lookup strategy.
   *
   * @return    test data path with {@link #guessTestDataLookupStrategy() guessed} lookup strategy
   * @throws IllegalStateException    as defined by {@link #getTestDataPath(TestDataLookupStrategy)}
   */
  @NonNls
  public static String getTestDataPath() throws IllegalStateException {
    TestDataLookupStrategy strategy = guessTestDataLookupStrategy();
    return getTestDataPath(strategy);
  }

  /**
   * Tries to return test data path for the given lookup strategy.
   *
   * @param strategy    lookup strategy to use
   * @return            test data path for the given strategy
   * @throws IllegalStateException    if it's not possible to find valid test data path for the given strategy
   */
  @NonNls
  public static String getTestDataPath(TestDataLookupStrategy strategy) throws IllegalStateException {
    String homePath = PathManager.getHomePath();

    List<String> relativePaths = TEST_DATA_RELATIVE_PATHS.get(strategy);
    if (relativePaths.isEmpty()) {
      throw new IllegalStateException(
        String.format("Can't determine test data path. Reason: no predefined relative paths are configured for test data "
                      + "lookup strategy %s. Configured mappings: %s", strategy, TEST_DATA_RELATIVE_PATHS)
      );
    }

    File candidate = null;
    for (String relativePath : relativePaths) {
      candidate = new File(homePath, relativePath);
      if (candidate.isDirectory()) {
        return candidate.getPath();
      }
    }

    if (candidate == null) {
      throw new IllegalStateException("Can't determine test data path. Looks like programming error - reached 'if' block that was "
                                      + "never expected to be executed");
    }
    return candidate.getPath();
  }

  /**
   * Tries to guess test data lookup strategy for the current execution.
   *
   * @return    guessed lookup strategy for the current execution; defaults to {@link TestDataLookupStrategy#ULTIMATE}
   */
  public static TestDataLookupStrategy guessTestDataLookupStrategy() {
    TestDataLookupStrategy result = guessTestDataLookupStrategyOnClassLocation();
    if (result == null) {
      result = guessTestDataLookupStrategyOnDirectoryAvailability();
    }
    return result;
  }

  @SuppressWarnings({"ThrowableInstanceNeverThrown"})
  @Nullable
  private static TestDataLookupStrategy guessTestDataLookupStrategyOnClassLocation() {

    // The general idea here is to find test class at the bottom of hierarchy and try to resolve test data lookup strategy
    // against it. Rationale is that there is a possible case that, say, 'ultimate' test class extends basic test class
    // that remains at 'community'. We want to perform the processing against 'ultimate' test class then.

    Class<?> testClass = null;
    StackTraceElement[] stackTrace = new Exception().getStackTrace();
    for (StackTraceElement stackTraceElement : stackTrace) {
      Class<?> clazz = loadClass(stackTraceElement.getClassName());
      if (TestCase.class != clazz && isJUnitClass(clazz)) {
        testClass = clazz;
      }
    }

    return testClass == null ? null : determineLookupStrategy(testClass);
  }

  private static Class<?> loadClass(String className) {
    Class<?> clazz = null;
    ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
    ClassLoader definingClassLoader = PathManagerEx.class.getClassLoader();
    ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();

    List<ClassLoader> classLoaders = asList(contextClassLoader, definingClassLoader, systemClassLoader);
    for (ClassLoader classLoader : classLoaders) {
      clazz = loadClass(className, classLoader);
      if (clazz != null) {
        break;
      }
    }

    if (clazz == null) {
      throw new IllegalStateException(String.format("Can't load class '%s'. Tried to do that via thread context class loader(%s), "
                                                    + "defining class loader(%s) and system class loader(%s)",
                                                    className, contextClassLoader, definingClassLoader, systemClassLoader));
    }
    return clazz;
  }

  @Nullable
  private static Class<?> loadClass(String className, ClassLoader classLoader) {
    try {
      return Class.forName(className, true, classLoader);
    }
    catch (ClassNotFoundException e) {
      return null;
    }
  }

  private static boolean isJUnitClass(Class<?> clazz) {
    return TestCase.class.isAssignableFrom(clazz) || TestRunnerUtil.isJUnit4TestClass(clazz);
  }

  @Nullable
  public static TestDataLookupStrategy determineLookupStrategy(Class<?> clazz) {
    FileSystemLocation projectLocation = parseProjectLocation();
    if (projectLocation == FileSystemLocation.COMMUNITY) {
      // There is no other options then.
      return TestDataLookupStrategy.COMMUNITY;
    }

    // Check if resulting strategy is already cached for the target class.
    TestDataLookupStrategy result = CLASS_STRATEGY_CACHE.get(clazz);
    if (result != null) {
      return result;
    }

    String targetFile = toSystemDependentName(clazz.getName().replace('.', '/') + ".java");
    if (PROCESSED_FILES.contains(targetFile)) {
      return null;
    }

    FileSystemLocation classFileLocation = new FileSystemLocationResolver(targetFile).resolve();
    PROCESSED_FILES.add(targetFile);

    if (classFileLocation == null) {
      return null;
    }

    // We know that project location is ULTIMATE if control flow reaches this place.
    result = classFileLocation == FileSystemLocation.COMMUNITY ? TestDataLookupStrategy.COMMUNITY_FROM_ULTIMATE
                                                               : TestDataLookupStrategy.ULTIMATE;
    CLASS_STRATEGY_CACHE.put(clazz, result);
    return result;
  }

  /**
   * Allows to determine project type by its file system location.
   *
   * @return    project type implied by its file system location
   */
  private static FileSystemLocation parseProjectLocation() {
    return new File(PathManager.getHomePath(), "community").isDirectory() ? FileSystemLocation.ULTIMATE : FileSystemLocation.COMMUNITY;
  }

  /**
   * Tries to check test data lookup strategy by target test data directories availability.
   * <p/>
   * Such an approach has a drawback that it doesn't work correctly at number of scenarios, e.g. when
   * <code>'community'</code> test is executed under <code>'ultimate'</code> project.
   *
   * @return    test data lookup strategy based on target test data directories availability
   */
  private static TestDataLookupStrategy guessTestDataLookupStrategyOnDirectoryAvailability() {
    String homePath = PathManager.getHomePath();
    for (Map.Entry<TestDataLookupStrategy, List<String>> entry : TEST_DATA_RELATIVE_PATHS.entrySet()) {
      for (String relativePath : entry.getValue()) {
        if (new File(homePath, relativePath).isDirectory()) {
          return entry.getKey();
        }
      }
    }
    return TestDataLookupStrategy.ULTIMATE;
  }

  /**
   * Allows to resolve {@link FileSystemLocation file type} by it's file system location.
   */
  private static class FileSystemLocationResolver {

    private final String rootPath = PathManager.getHomePath();
    private final String myTargetFile;
    private FileSystemLocation myResult;

    FileSystemLocationResolver(String targetFile) {
      myTargetFile = targetFile;
    }

    /**
     * @return    resolved file system location type for the target file defined at constructor if possible; <code>null</code> otherwise
     */
    @Nullable
    public FileSystemLocation resolve() {
      myResult = null;
      dispatch(new File(rootPath));
      return myResult;
    }

    public void dispatch(File dir) {
      if (myResult != null) {
        return;
      }
      for (File file : dir.listFiles()) {
        if (myResult != null) {
          return;
        }
        if (file.isDirectory()) {
          dispatch(file);
        }
        else {
          check(file);
        }
      }
    }

    public void check(File file) {
      String path = file.getAbsolutePath();
      if (path.endsWith(myTargetFile)) {
        String targetPathSegment = path.substring(PathManager.getHomePath().length() + 1);
        myResult = targetPathSegment.startsWith("community") ? FileSystemLocation.COMMUNITY : FileSystemLocation.ULTIMATE;
      }
    }
  }
}
