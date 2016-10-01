/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.antuitest.tasks;

import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.google.common.collect.ImmutableList;
import org.apache.tools.ant.Project;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runner.Runner;

import java.io.File;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests sharding logic in {@link UiTestTask}.
 */
public class UiTestTaskTest {

  private UiTestTask task;
  private File classpathFile;

  @Before
  public void setUp() throws Exception {
    task = new UiTestTask();
    classpathFile = File.createTempFile("classpath", null);
  }

  @After
  public void tearDown() throws Exception {
    classpathFile.delete();
  }

  @Test
  public void testBatchComputation() throws Exception {
    task.setProject(new Project());
    task.setTestSuite("com.android.antuitest.tasks.UiTestTaskTest");
    task.setClasspathFile(classpathFile.getAbsolutePath());

    assertThat(task.getTestGroups()).containsExactly(
      "THEME", ImmutableList.of(ATest.class),
      "EDITING", ImmutableList.of(BTest.class, CTest.class),
      "DEFAULT", ImmutableList.of(DTest.class),
      "SpecialATest", ImmutableList.of(SpecialATest.class),
      "SpecialBTest", ImmutableList.of(SpecialBTest.class));
  }
}

abstract class GuiTestRunner extends Runner {}

@RunIn(TestGroup.THEME)
@RunWith(GuiTestRunner.class)
class ATest {}

@RunIn(TestGroup.EDITING)
@RunWith(GuiTestRunner.class)
class BTest {}

@RunIn(TestGroup.EDITING)
@RunWith(GuiTestRunner.class)
class CTest {}

@RunWith(GuiTestRunner.class)
class DTest {}

@RunIn(TestGroup.INDIVIDUAL)
@RunWith(GuiTestRunner.class)
class SpecialATest {}

@RunIn(TestGroup.INDIVIDUAL)
@RunWith(GuiTestRunner.class)
class SpecialBTest {}
