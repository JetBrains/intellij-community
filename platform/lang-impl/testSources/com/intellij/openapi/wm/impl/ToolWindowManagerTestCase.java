// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.SkipInHeadlessEnvironment;

/**
 * @author Dmitry Avdeev
 */
@SkipInHeadlessEnvironment
public abstract class ToolWindowManagerTestCase extends LightPlatformCodeInsightTestCase {
  protected ToolWindowManagerImpl myManager;
  private ToolWindowManagerEx myOldManager;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myManager = new ToolWindowManagerImpl(getProject(), WindowManagerEx.getInstanceEx(), ActionManager.getInstance()) {
      @Override
      protected void fireStateChanged() {
      }
    };
    Disposer.register(getTestRootDisposable(), myManager);
    myOldManager = (ToolWindowManagerEx)((ComponentManagerImpl)getProject()).registerComponentInstance(ToolWindowManager.class, myManager);
    myManager.init();
  }

  @Override
  public void tearDown() throws Exception {
    try {
      myManager.projectClosed();
      myManager = null;
      ((ComponentManagerImpl)getProject()).registerComponentInstance(ToolWindowManager.class, myOldManager);
      myOldManager = null;
    }
    finally {
      super.tearDown();
    }
  }
}
