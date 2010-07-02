/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import org.jetbrains.annotations.NonNls;

/**
 * A testcase that provides IDEA application and project. Note both are reused for each test run in the session so
 * be careful to return all the modification made to application and project components (such as settings) after
 * test is finished so other test aren't affected. The project is initialized with single module that have single
 * content&amp;source entry. For your convenience the project may be equipped with some mock JDK so your tests may
 * refer to external classes. In order to enable this feature you have to have a folder named "mockJDK" under
 * idea installation home that is used for test running. Place src.zip under that folder. We'd suggest this is real mock
 * so it contains classes that is really needed in order to speed up tests startup.
 */
@SuppressWarnings({"HardCodedStringLiteral"})
@NonNls public class LightIdeaTestCase extends LightPlatformTestCase {
  public LightIdeaTestCase() {
    IdeaTestCase.initPlatformPrefix();
  }

  public static JavaPsiFacadeEx getJavaFacade() {
    return JavaPsiFacadeEx.getInstanceEx(ourProject);
  }


  @Override
  protected Sdk getProjectJDK() {
    return JavaSdkImpl.getMockJdk17();
  }

  protected ModuleType getModuleType() {
    return StdModuleTypes.JAVA;
  }
}
