// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import org.jetbrains.annotations.NotNull;

import static com.intellij.workspaceModel.ide.legacyBridge.impl.java.JavaModuleTypeUtils.JAVA_MODULE_ENTITY_TYPE_ID_NAME;

/**
 * A test case that provides IDEA application and project. Note both are reused for each test run in the session so
 * be careful to return all the modification made to application and project components (such as settings) after
 * test is finished so other test aren't affected. The project is initialized with single module that have single
 * content and source entry. For your convenience the project may be equipped with some mock JDK so your tests may
 * refer to external classes. In order to enable this feature you have to have a folder named "mockJDK" under
 * idea installation home that is used for test running. Place src.zip under that folder. We'd suggest this to be real mock SDK
 * (i.e. it should contain only classes that are really needed in your test) in order to speed up tests startup.
 * Since the light project is a test speed optimization and thus a quite leaky abstraction at that, there are a number of restrictions the light test should obey:
 * <li>The test should not depend on any way on the project name, location, internal project files, like "*.iml". All these can change unpredictably or be absent.</li>
 * <li>The test should not modify the project components in any way to reduce interference with the next test. Or, it should revert all changes in the end</li>
 * <li>The test should not count on usual project lifecycle, like "onProjectClose()" event or project being disposed on close, etc. All these can be absent.</li>
 */
public abstract class LightIdeaTestCase extends LightPlatformTestCase {
  public JavaPsiFacadeEx getJavaFacade() {
    return JavaPsiFacadeEx.getInstanceEx(getProject());
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk17();
  }

  @Override
  protected @NotNull String getModuleTypeId() {
    return JAVA_MODULE_ENTITY_TYPE_ID_NAME;
  }
}
