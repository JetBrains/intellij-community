/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.service.task.ui;

import com.intellij.openapi.command.impl.DummyProject;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskExecutionInfo;
import com.intellij.openapi.externalSystem.test.ExternalSystemTestUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.util.containers.ContainerUtilRt;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

/**
 * @author Vladislav Soroka
 * @since 8/13/13
 */
public class ExternalSystemRecentTaskListModelTest {
  
  private ExternalSystemRecentTaskListModel myModel;

  @Before
  public void setUp() {
    myModel = new ExternalSystemRecentTaskListModel(ExternalSystemTestUtil.TEST_EXTERNAL_SYSTEM_ID, DummyProject.getInstance());
  }
  
  @Test
  public void testSetFirst() throws Exception {
    List<ExternalTaskExecutionInfo> tasks = ContainerUtilRt.newArrayList();
    for (int i = 0; i <= ExternalSystemConstants.RECENT_TASKS_NUMBER; i++) {
      new ExternalTaskExecutionInfo(new ExternalSystemTaskExecutionSettings(), "task" + i);
    }
    myModel.setTasks(tasks);

    myModel.setFirst(new ExternalTaskExecutionInfo(new ExternalSystemTaskExecutionSettings(), "newTask"));

    Assert.assertEquals(ExternalSystemConstants.RECENT_TASKS_NUMBER, myModel.getSize());
    myModel.setFirst(new ExternalTaskExecutionInfo(new ExternalSystemTaskExecutionSettings(), "task1"));
    Assert.assertEquals(ExternalSystemConstants.RECENT_TASKS_NUMBER, myModel.getSize());
  }

  @Test
  public void testEnsureSize() throws Exception {
    List<ExternalTaskExecutionInfo> tasks = ContainerUtilRt.newArrayList();

    // test task list widening
    myModel.setTasks(tasks);
    myModel.ensureSize(ExternalSystemConstants.RECENT_TASKS_NUMBER);
    Assert.assertEquals("task list widening failed", ExternalSystemConstants.RECENT_TASKS_NUMBER, myModel.getSize());

    // test task list reduction
    for (int i = 0; i < ExternalSystemConstants.RECENT_TASKS_NUMBER + 1; i++) {
      tasks.add(new ExternalTaskExecutionInfo(new ExternalSystemTaskExecutionSettings(), "task" + i));
    }
    myModel.setTasks(tasks);
    Assert.assertEquals(ExternalSystemConstants.RECENT_TASKS_NUMBER + 1, myModel.getSize());

    myModel.ensureSize(ExternalSystemConstants.RECENT_TASKS_NUMBER);
    Assert.assertEquals("task list reduction failed", ExternalSystemConstants.RECENT_TASKS_NUMBER, myModel.getSize());
  }
}
