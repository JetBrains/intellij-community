/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.testIntegration;

import com.intellij.execution.testframework.sm.runner.states.TestStateInfo;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude.*;
import static com.intellij.testIntegration.TestInfo.select;
import static com.intellij.testIntegration.TestInfo.selectNot;

public class RecentTestsData {
  private static Comparator<TestInfo> TEST_BY_PATH_COMPARATOR = new Comparator<TestInfo>() {
    @Override
    public int compare(TestInfo o1, TestInfo o2) {
      String path1 = VirtualFileManager.extractPath(o1.getUrl());
      String path2 = VirtualFileManager.extractPath(o2.getUrl());
      return path1.compareTo(path2);
    }
  };
  
  private static Comparator<SuiteInfo> SUITE_BY_RECENT_COMPARATOR = new Comparator<SuiteInfo>() {
    @Override
    public int compare(SuiteInfo o1, SuiteInfo o2) {
      return -o1.getMostRecentRunDate().compareTo(o2.getMostRecentRunDate());
    }
  };

  private static Comparator<TestInfo> TEST_BY_RECENT_COMPARATOR = new Comparator<TestInfo>() {
    @Override
    public int compare(TestInfo o1, TestInfo o2) {
      return -o1.getRunDate().compareTo(o2.getRunDate());
    }
  };

  private final RecentTestRunner myRunner;
  private final Map<String, SuiteInfo> mySuites = ContainerUtil.newHashMap();

  private List<TestInfo> myTestsWithoutSuites = ContainerUtil.newArrayList();

  public RecentTestsData(RecentTestRunner runner) {
    myRunner = runner;
  }

  public void addTest(String url, TestStateInfo.Magnitude magnitude, Date runDate) {
    if (myRunner.isSuite(url)) {
      mySuites.put(url, new SuiteInfo(url, magnitude, runDate));
      return;
    }

    TestInfo testInfo = new TestInfo(url, magnitude, runDate);

    SuiteInfo suite = getSuite(url);
    if (suite != null) {
      suite.addTest(testInfo);
      return;
    }

    myTestsWithoutSuites.add(testInfo);
  }

  @Nullable
  private SuiteInfo getSuite(String url) {
    String testName = VirtualFileManager.extractPath(url);
    
    for (SuiteInfo info : mySuites.values()) {
      String suiteName = info.getSuiteName();
      if (testName.startsWith(suiteName)) {
        return info;
      }
    }
    
    return null;
  }

  public List<String> calculateTestList() {
    distributeUnmatchedTests();

    List<SuiteInfo> suites = ContainerUtil.newArrayList(mySuites.values());
    Collections.sort(suites, TEST_BY_PATH_COMPARATOR);
    Collections.sort(myTestsWithoutSuites, TEST_BY_PATH_COMPARATOR);
    
    Collections.sort(suites, SUITE_BY_RECENT_COMPARATOR);
    Collections.sort(myTestsWithoutSuites, TEST_BY_RECENT_COMPARATOR);
    
    List<String> result = ContainerUtil.newArrayList();

    fillWithSuites(result, select(suites, ERROR_INDEX));
    fillWithTests(result, select(myTestsWithoutSuites, ERROR_INDEX));

    fillWithSuites(result, selectNot(suites, ERROR_INDEX, COMPLETE_INDEX, PASSED_INDEX));
    fillWithTests(result, selectNot(myTestsWithoutSuites, COMPLETE_INDEX, PASSED_INDEX));

    fillWithSuites(result, select(suites, COMPLETE_INDEX, PASSED_INDEX));
    fillWithTests(result, select(myTestsWithoutSuites, COMPLETE_INDEX, PASSED_INDEX));
    
    return result;
  }

  private static void fillWithTests(List<String> result, List<TestInfo> tests) {
    for (TestInfo info : tests) {
      result.add(info.getUrl());
    }
  }

  private static void fillWithSuites(List<String> result, List<SuiteInfo> suites) {
    for (SuiteInfo suite : suites) {
      result.addAll(suiteToTestList(suite));
    }
  }

  private static List<String> suiteToTestList(SuiteInfo suite) {
    List<String> result = ContainerUtil.newArrayList();

    TestStateInfo.Magnitude suiteMagnitude = suite.getMagnitude();
    Set<TestInfo> allTests = suite.getTests();

    List<TestInfo> sameMagnitudeTests = suite.getTests(suiteMagnitude);
    if (sameMagnitudeTests.size() == allTests.size()) {
      result.add(suite.getUrl());
    }
    else {
      result.add(suite.getUrl());
      Collections.sort(sameMagnitudeTests, TEST_BY_RECENT_COMPARATOR);
      for (TestInfo test : sameMagnitudeTests) {
        result.add(test.getUrl());
      }
    }

    return result;
  }


  private void distributeUnmatchedTests() {
    List<TestInfo> noSuites = ContainerUtil.newSmartList();

    for (TestInfo test : myTestsWithoutSuites) {
      String url = test.getUrl();
      SuiteInfo suite = getSuite(url);
      if (suite != null) {
        suite.addTest(test);
      }
      else {
        noSuites.add(test);
      }
    }

    myTestsWithoutSuites = noSuites;
  }
}


class SuiteInfo extends TestInfo {
  private final String mySuiteName;
  private Set<TestInfo> tests = ContainerUtil.newHashSet();

  public SuiteInfo(String url, TestStateInfo.Magnitude magnitude, Date runDate) {
    super(url, magnitude, runDate);
    mySuiteName = VirtualFileManager.extractPath(url);
  }
  
  public Date getMostRecentRunDate() {
    Date mostRecent = getRunDate();
    for (TestInfo test : tests) {
      Date testDate = test.getRunDate();
      if (testDate.compareTo(mostRecent) > 0) {
        mostRecent = testDate;
      }
    }
    return mostRecent;
  }
  
  public String getSuiteName() {
    return mySuiteName;
  }

  public void addTest(TestInfo info) {
    tests.add(info);
  }

  public Set<TestInfo> getTests() {
    return tests;
  }

  public List<TestInfo> getTests(TestStateInfo.Magnitude magnitude) {
    return select(tests, magnitude);
  }
}

class TestInfo {
  private final Date runDate;
  private final String url;
  private final TestStateInfo.Magnitude magnitude;

  public TestInfo(String url, TestStateInfo.Magnitude magnitude, Date runDate) {
    this.url = url;
    this.magnitude = magnitude;
    this.runDate = runDate;
  }
  
  public Date getRunDate() {
    return runDate;
  }

  public String getUrl() {
    return url;
  }

  public TestStateInfo.Magnitude getMagnitude() {
    return magnitude;
  }

  public static <T extends TestInfo> List<T> select(Collection<T> infos, final TestStateInfo.Magnitude... magnitudes) {
    return ContainerUtil.filter(infos, new Condition<T>() {
      @Override
      public boolean value(T t) {
        for (TestStateInfo.Magnitude magnitude : magnitudes) {
          if (t.getMagnitude() == magnitude) {
            return true;
          }
        }
        return false;
      }
    });
  }

  public static <T extends TestInfo> List<T> selectNot(Collection<T> infos, final TestStateInfo.Magnitude... magnitudes) {
    return ContainerUtil.filter(infos, new Condition<T>() {
      @Override
      public boolean value(T t) {
        for (TestStateInfo.Magnitude magnitude : magnitudes) {
          if (t.getMagnitude() == magnitude) {
            return false;
          }
        }
        return true;
      }
    });
  }
}
