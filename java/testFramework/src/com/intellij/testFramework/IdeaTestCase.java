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
 * @author mike
 */
@NonNls public abstract class IdeaTestCase extends PlatformTestCase {
  public final JavaPsiFacadeEx getJavaFacade() {
    return JavaPsiFacadeEx.getInstanceEx(myProject);
  }

  @Override
  protected Sdk getTestProjectJdk() {
    return JavaSdkImpl.getMockJdk("java 1.4");
  }

  @Override
  protected ModuleType getModuleType() {
    return StdModuleTypes.JAVA;
  }

  //private void cleanTheWorld() throws IllegalAccessException, NoSuchFieldException {
  //  try {
  //    ((JobSchedulerImpl)JobScheduler.getInstance()).waitForCompletion();
  //  }
  //  catch (Throwable throwable) {
  //    LOG.error(throwable);
  //  }
  //  UIUtil.dispatchAllInvocationEvents();
  //
  //  Thread thread = Thread.currentThread();
  //  Field locals = Thread.class.getDeclaredField("threadLocals");
  //  locals.setAccessible(true);
  //  locals.set(thread, null);
  //
  //  String path = HeapWalker.findObjectUnder(ourApplication, Project.class);
  //  if (path != null) {
  //    throw new RuntimeException(getName() + " " + path);
  //  }
  //}

  static {
    System.setProperty("jbdt.test.fixture", "com.intellij.designer.dt.IJTestFixture");
  }
}
