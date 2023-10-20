// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.roots;

import com.intellij.idea.TestFor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jdom.Element;
import org.jdom.JDOMException;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.testFramework.assertions.Assertions.assertThat;

public class ProjectRootManagerImplTest extends HeavyPlatformTestCase {
  @TestFor(issues = "IDEA-232634")
  public void testLoadStateFiresJdkChange() throws IOException, JDOMException {
    AtomicInteger count = new AtomicInteger(0);
    ProjectRootManagerEx.getInstanceEx(myProject).addProjectJdkListener(() -> {
      ApplicationManager.getApplication().assertWriteAccessAllowed();
      count.incrementAndGet();
    });

    ProjectRootManagerImpl impl = ProjectRootManagerImpl.getInstanceImpl(myProject);
    Element firstLoad = JDOMUtil.load("""
                                   <component name="ProjectRootManager" version="2" languageLevel="JDK_11" default="true" project-jdk-name="corretto-11" project-jdk-type="JavaSDK">
                                     <output url="file://$PROJECT_DIR$/out" />
                                   </component>
                                   """);
    Element secondLoad = JDOMUtil.load("""
                                   <component name="ProjectRootManager" version="2" languageLevel="JDK_11" default="true" project-jdk-name="corretto-11" project-jdk-type="JavaSDK">
                                     <output url="file://$PROJECT_DIR$/out2" />
                                   </component>
                                   """);
    impl.loadState(firstLoad);
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    impl.loadState(secondLoad);
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

    assertThat(count).hasValueGreaterThanOrEqualTo(2);
  }

  @TestFor(issues = "IDEA-330499")
  public void testNoEventsIfNothingChanged() throws IOException, JDOMException {
    AtomicInteger count = new AtomicInteger(0);
    ProjectRootManagerEx.getInstanceEx(myProject).addProjectJdkListener(() -> {
      ApplicationManager.getApplication().assertWriteAccessAllowed();
      count.incrementAndGet();
    });

    ProjectRootManagerImpl impl = ProjectRootManagerImpl.getInstanceImpl(myProject);
    Element firstLoad = JDOMUtil.load("""
                                        <component name="ProjectRootManager" version="2" languageLevel="JDK_11" default="true" project-jdk-name="corretto-11" project-jdk-type="JavaSDK">
                                          <output url="file://$PROJECT_DIR$/out" />
                                        </component>
                                        """);
    Element secondLoad = JDOMUtil.load("""
                                         <component name="ProjectRootManager" version="2" languageLevel="JDK_11" default="true" project-jdk-name="corretto-11" project-jdk-type="JavaSDK">
                                           <output url="file://$PROJECT_DIR$/out2" />
                                         </component>
                                         """);
    impl.loadState(firstLoad);
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    assertEquals(1, count.get());

    impl.loadState(secondLoad);
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    assertEquals(2, count.get());

    impl.loadState(secondLoad);
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    assertEquals(2, count.get());
  }
}
