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

import com.intellij.execution.TestStateStorage;
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude.*;
import static com.intellij.testIntegration.TestInfo.select;


public class RecentTestsListProvider {
  private final Map<String, TestStateStorage.Record> myRecords;

  public RecentTestsListProvider(Map<String, TestStateStorage.Record> records) {
    myRecords = records;  
  }
  
  public List<String> getUrlsToShowFromHistory() {
    if (myRecords == null) return ContainerUtil.emptyList();
    
    RecentTestsData data = new RecentTestsData();
    for (Map.Entry<String, TestStateStorage.Record> entry : myRecords.entrySet()) {
      String url = entry.getKey();
      TestStateStorage.Record record = entry.getValue();
      if (TestLocator.canLocate(url)) {
        data.addTest(url, getMagnitude(record.magnitude), record.date);
      }
    }

    return data.getSortedTestsList();
  }
  
  private static TestStateInfo.Magnitude getMagnitude(int magnitude) {
    for (TestStateInfo.Magnitude m : values()) {
      if (m.getValue() == magnitude) {
        return m;
      }
    }
    return null;
  }
}

class RecentTestsData {
  private static Comparator<TestInfo> BY_PATH_COMPARATOR = new Comparator<TestInfo>() {
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
  
  private final Map<String, SuiteInfo> mySuites = ContainerUtil.newHashMap();

  private List<TestInfo> myTestsWithoutSuites = ContainerUtil.newArrayList();

  public void addTest(String url, TestStateInfo.Magnitude magnitude, Date runDate) {
    if (TestLocator.isSuite(url)) {
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

  public List<String> getSortedTestsList() {
    distributeUnmatchedTests();
    List<String> result = ContainerUtil.newArrayList();
    fillWithTests(result, ERROR_INDEX, FAILED_INDEX);
    fillWithTests(result, COMPLETE_INDEX, PASSED_INDEX, IGNORED_INDEX);
    return result;
  }
  
  private void fillWithTests(List<String> result, TestStateInfo.Magnitude... magnitudes) {
    List<SuiteInfo> suites = ContainerUtil.newArrayList(mySuites.values());
    
    List<SuiteInfo> failedSuites = select(suites, magnitudes);
    List<TestInfo> failedTests = select(myTestsWithoutSuites, magnitudes);

    sortByPath(failedSuites);
    sortByPath(failedTests);

    sortSuitesByRecent(failedSuites);
    sortTestsByRecent(failedTests);

    fillWithSuites(result, failedSuites);
    fillWithTests(result, failedTests);
  }

  private static void sortSuitesByRecent(List<SuiteInfo> suites) {
    Collections.sort(suites, SUITE_BY_RECENT_COMPARATOR);
  }

  private static void sortTestsByRecent(List<TestInfo> tests) {
    Collections.sort(tests, TEST_BY_RECENT_COMPARATOR);
  }

  private static void sortByPath(List<? extends TestInfo> list) {
    Collections.sort(list, BY_PATH_COMPARATOR);
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
    
    if (suite.canTrustSuiteMagnitude() && suite.isPassed()) {
      result.add(suite.getUrl());
      return result;
    }

    List<TestInfo> failedTests = suite.getFailedTests();
    sortTestsByRecent(failedTests);
    
    if (failedTests.size() == suite.getTotalTestsCount()) {
      result.add(suite.getUrl());  
    }
    else if (failedTests.size() < 3) {
      result.addAll(ContainerUtil.map(failedTests, new Function<TestInfo, String>() {
        @Override
        public String fun(TestInfo testInfo) {
          return testInfo.getUrl();
        }
      }));
      result.add(suite.getUrl());
    }
    else {
      result.add(suite.getUrl());
      result.addAll(ContainerUtil.map(failedTests, new Function<TestInfo, String>() {
        @Override
        public String fun(TestInfo testInfo) {
          return testInfo.getUrl();
        }
      }));
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
  
  public boolean canTrustSuiteMagnitude() {
    Date suiteRunDate = getRunDate();
    for (TestInfo test : tests) {
      if (test.getRunDate().getTime() > suiteRunDate.getTime()) {
        return false;
      }
    }
    return true;
  }
  
  public boolean isPassed() {
    return getMagnitude() == IGNORED_INDEX || getMagnitude() == PASSED_INDEX || getMagnitude() == COMPLETE_INDEX;
  }
  
  public List<TestInfo> getFailedTests() {
    List<TestInfo> failed = ContainerUtil.newSmartList();
    for (TestInfo test : tests) {
      if (test.getMagnitude() == FAILED_INDEX || test.getMagnitude() == ERROR_INDEX) {
        failed.add(test);
      }
    }
    return failed;
  } 
  
  public String getSuiteName() {
    return mySuiteName;
  }

  public void addTest(TestInfo info) {
    tests.add(info);
  }
  
  public int getTotalTestsCount() {
    return tests.size();
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
}
