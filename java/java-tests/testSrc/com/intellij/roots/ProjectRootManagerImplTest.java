// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.roots;

import com.intellij.idea.TestFor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jdom.Element;

import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.testFramework.assertions.Assertions.assertThat;

public class ProjectRootManagerImplTest extends HeavyPlatformTestCase {
  @TestFor(issues = "IDEA-232634")
  public void testLoadStateFiresJdkChange() {
    AtomicInteger count = new AtomicInteger(0);
    ProjectRootManagerEx.getInstanceEx(myProject).addProjectJdkListener(() -> {
      ApplicationManager.getApplication().assertWriteAccessAllowed();
      count.incrementAndGet();
    });

    ProjectRootManagerImpl impl = ProjectRootManagerImpl.getInstanceImpl(myProject);
    Element oldState = impl.getState();
    impl.loadState(new Element("empty"));
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    impl.loadState(oldState);
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

    assertThat(count).hasValueGreaterThanOrEqualTo(2);
  }
}
