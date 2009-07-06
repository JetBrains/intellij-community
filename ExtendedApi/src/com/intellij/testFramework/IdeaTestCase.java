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
