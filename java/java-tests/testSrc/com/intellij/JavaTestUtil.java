package com.intellij;

import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.TestRunnerUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static java.util.Arrays.asList;

/**
 * @author yole
 */
public class JavaTestUtil {

  @NonNls private static final String JAVA_TEST_DATA = "java/java-tests/testData";

  public static String getJavaTestDataPath() {
    final String homePath = PathManager.getHomePath();
    File dir = new File(homePath, "community/" + JAVA_TEST_DATA);
    if (dir.exists()) {
      return dir.getPath();
    }
    return new File(homePath, JAVA_TEST_DATA).getPath();
  }

  private enum TestGroup {
    ULTIMATE, COMMUNITY
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
  private static final Map<TestGroup, List<String>> TEST_DATA_RELATIVE_PATHS = new EnumMap<TestGroup, List<String>>(TestGroup.class);
  static {
    TEST_DATA_RELATIVE_PATHS.put(TestGroup.ULTIMATE, Collections.singletonList(toSystemDependentName("/testData")));

    List<String> communityPathEndings = new ArrayList<String>();
    String communityEnding = toSystemDependentName("java/java-tests/testData");
    communityPathEndings.add(toSystemDependentName("community/" + communityEnding));
    communityPathEndings.add(communityEnding);
    TEST_DATA_RELATIVE_PATHS.put(TestGroup.COMMUNITY, communityPathEndings);
  }

  private JavaTestUtil() {
  }

  @NonNls
  public static String getJavaTestDataPath2() {
    String homePath = PathManager.getHomePath();
    TestGroup testGroup = determineTestGroup();
    List<String> relativePaths = TEST_DATA_RELATIVE_PATHS.get(testGroup);
    if (relativePaths.isEmpty()) {
      throw new IllegalStateException(
        String.format("Can't determine test data path. Reason: no predefined relative paths are configured for test group %s. "
                      + "Configured mappings: %s", testGroup, TEST_DATA_RELATIVE_PATHS)
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

  @SuppressWarnings({"ThrowableInstanceNeverThrown"})
  private static TestGroup determineTestGroup() {
    for (StackTraceElement stackTraceElement : new Exception().getStackTrace()) {
      Class<?> clazz = loadClass(stackTraceElement.getClassName());
      if (isJUnitClass(clazz)) {
        return determineTestGroup(clazz);
      }
    }
    return TestGroup.ULTIMATE;
  }

  private static Class<?> loadClass(String className) {
    Class<?> clazz = null;
    ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
    ClassLoader definingClassLoader = JavaTestUtil.class.getClassLoader();
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

  public static TestGroup determineTestGroup(Class<?> clazz) {
    String rootPath = PathManager.getResourceRoot(clazz, toSystemDependentName(clazz.getName().replace('.', '/') + ".class"));
    return rootPath != null && rootPath.indexOf("community") >= 0 ? TestGroup.COMMUNITY : TestGroup.ULTIMATE;
  }
}
