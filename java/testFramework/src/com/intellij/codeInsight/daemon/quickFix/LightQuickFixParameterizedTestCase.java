/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.openapi.application.ex.PathManagerEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RunWith(com.intellij.testFramework.Parameterized.class)
public abstract class LightQuickFixParameterizedTestCase extends LightQuickFixTestCase {

  @Parameterized.Parameter
  public String myFileSuffix;

  @Parameterized.Parameter(1)
  public String myTestDataPath;

  @com.intellij.testFramework.Parameterized.Parameters(name = "{0}")
  public static List<Object[]> params(Class<?> klass) throws Throwable{
    final QuickFixTestCase testCase = ((LightQuickFixTestCase)klass.newInstance()).createWrapper();
    final String path = testCase.getBasePath();

    assertNotNull("getBasePath() should not return null!", path);

    PathManagerEx.replaceLookupStrategy(klass, LightQuickFixTestCase.class, com.intellij.testFramework.Parameterized.class);

    String testDataPath = testCase.getTestDataPath();
    final String testDirPath = testDataPath.replace(File.separatorChar, '/') + path;
    File testDir = new File(testDirPath);
    final File[] files = testDir.listFiles(new FilenameFilter() {
      @Override
      public boolean accept(File dir, @NonNls String name) {
        return name.startsWith(BEFORE_PREFIX);
      }
    });

    if (files == null) {
      fail("Test files not found in " + testDirPath);
    }

    final List<Object[]> result = new ArrayList<Object[]>();
    for (File file : files) {
      final String testName = file.getName().substring(BEFORE_PREFIX.length());
      result.add(new Object[] {testName, testDataPath});
    }
    return result;
  }

  @Parameterized.Parameters(name = "{0}")
  public static List<Object[]> params() throws Throwable{
    return Collections.emptyList();
  }

  @Override
  protected void doAllTests() {
    super.doAllTests();
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    if (myTestDataPath != null) {
      return myTestDataPath;
    }
    return super.getTestDataPath();
  }

  @Override
  public String getName() {
    if (myFileSuffix != null) {
      return "test" + myFileSuffix;
    }
    return super.getName();
  }

  @Before
  public void before() throws Throwable {
    final Throwable[] throwables = new Throwable[1];

    invokeTestRunnable(new Runnable() {
      @Override
      public void run() {
        try {
          LightQuickFixParameterizedTestCase.this.setUp();
        }
        catch (Throwable e) {
          throwables[0] = e;
        }
      }
    });

    if (throwables[0] != null) {
      throw throwables[0];
    }
  }

  @After
  public void after() throws Throwable {
    final Throwable[] throwables = new Throwable[1];

    invokeTestRunnable(new Runnable() {
      @Override
      public void run() {
        try {
          LightQuickFixParameterizedTestCase.this.tearDown();
        }
        catch (Throwable e) {
          throwables[0] = e;
        }
      }
    });
    if (throwables[0] != null) {
      throw throwables[0];
    }
  }

  @Test
  public void runSingle() throws Throwable {
    final Throwable[] throwables = new Throwable[1];

    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        try {
          doSingleTest(myFileSuffix, myTestDataPath);
        }
        catch (Throwable e) {
          throwables[0] = e;
        }
      }
    };

    invokeTestRunnable(runnable);

    if (throwables[0] != null) {
      throw throwables[0];
    }
    
  }
}
