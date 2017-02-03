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
    myManager = new ToolWindowManagerImpl(getProject(), WindowManagerEx.getInstanceEx(), ActionManager.getInstance());
    Disposer.register(getTestRootDisposable(), myManager);
    myOldManager = (ToolWindowManagerEx)((ComponentManagerImpl)getProject()).registerComponentInstance(ToolWindowManager.class, myManager);
    myManager.projectOpened();
  }

  @Override
  public void tearDown() throws Exception {
    try {
      myManager.projectClosed();
      ((ComponentManagerImpl)getProject()).registerComponentInstance(ToolWindowManager.class, myOldManager);
    }
    finally {
      super.tearDown();
    }
  }
}
