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

import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.intellij.openapi.util.io.FileUtil;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.Map;

import static junit.framework.Assert.assertTrue;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.fail;

/**
 * Tests sharding logic in {@link UiTestTask}.
 */
public class UiTestTaskTest {

  private UiTestTask task;
  private File classpathFile;

  @Before
  public void setUp() throws Exception {
    task = new UiTestTask();
    classpathFile = FileUtil.createTempFile("classpath", null);
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

    Map<String, List<Class<?>>> batches = task.getTestGroups();
    assertEquals(3, batches.size());

    assertTrue(batches.containsKey("A"));
    assertEquals("com.android.antuitest.tasks.ATest,", task.getTestSpec(batches.get("A")));

    assertTrue(batches.containsKey("B"));
    assertEquals("com.android.antuitest.tasks.BTest,com.android.antuitest.tasks.CTest,", task.getTestSpec(batches.get("B")));

    assertTrue(batches.containsKey("DEFAULT"));
    assertEquals("com.android.antuitest.tasks.DTest,", task.getTestSpec(batches.get("DEFAULT")));
  }
}

@BelongsToTestGroups(TestGroup.A)
class ATest extends GuiTestCase { }

@BelongsToTestGroups(TestGroup.B)
class BTest extends GuiTestCase { }

@BelongsToTestGroups(TestGroup.B)
class CTest extends GuiTestCase { }

class DTest extends GuiTestCase { }
