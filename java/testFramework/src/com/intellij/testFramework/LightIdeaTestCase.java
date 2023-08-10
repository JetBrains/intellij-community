/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.testFramework;

import com.intellij.openapi.module.ModuleTypeId;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import org.jetbrains.annotations.NotNull;

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

  @NotNull
  @Override
  protected String getModuleTypeId() {
    return ModuleTypeId.JAVA_MODULE;
  }
}
