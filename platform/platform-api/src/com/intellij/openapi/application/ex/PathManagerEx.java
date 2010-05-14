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
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.*;

import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;

public class PathManagerEx {
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
    TEST_DATA_RELATIVE_PATHS.put(TestDataLookupStrategy.ULTIMATE, Collections.singletonList(toSystemDependentName("/testData")));

    List<String> communityPathEndings = new ArrayList<String>();
    String communityEnding = toSystemDependentName("java/java-tests/testData");
    communityPathEndings.add(toSystemDependentName("community/" + communityEnding));
    communityPathEndings.add(communityEnding);
    TEST_DATA_RELATIVE_PATHS.put(TestDataLookupStrategy.COMMUNITY, communityPathEndings);

    List<String> communityFromUltimatePathEndings = new ArrayList<String>(communityPathEndings);
    Collections.reverse(communityFromUltimatePathEndings);
    TEST_DATA_RELATIVE_PATHS.put(TestDataLookupStrategy.COMMUNITY_FROM_ULTIMATE, communityFromUltimatePathEndings);
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
}
