// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.wm.impl.ProjectFrameHelper;
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.SkipInHeadlessEnvironment;

/**
 * @author Dmitry Avdeev
 */
@SkipInHeadlessEnvironment
public abstract class ToolWindowManagerTestCase extends LightPlatformCodeInsightTestCase {
  protected ToolWindowManagerImpl manager;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    Project project = getProject();
    manager = new ToolWindowManagerImpl(project) {
      @Override
      protected void fireStateChanged() {
      }
    };
    ServiceContainerUtil.replaceService(project, ToolWindowManager.class, manager, getTestRootDisposable());

    ProjectFrameHelper frame = new ProjectFrameHelper(new IdeFrameImpl(), null);
    frame.init();
    manager.doInit(frame, project.getMessageBus().connect(getTestRootDisposable()));
  }

  @Override
  public void tearDown() throws Exception {
    try {
      manager.projectClosed();
      manager = null;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }
}
