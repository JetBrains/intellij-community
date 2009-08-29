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
 * content&amp;source entry. For your convinience the project may be equipped with some mock JDK so your tests may
 * refer to external classes. In order to enable this feature you have to have a folder named "mockJDK" under
 * idea installation home that is used for test running. Place src.zip under that folder. We'd suggest this is real mock
 * so it contains classes that is really needed in order to speed up tests startup.
 */
@SuppressWarnings({"HardCodedStringLiteral"})
@NonNls public class LightIdeaTestCase extends LightPlatformTestCase {

  public static JavaPsiFacadeEx getJavaFacade() {
    return JavaPsiFacadeEx.getInstanceEx(ourProject);
  }


  @Override
  protected Sdk getProjectJDK() {
    return JavaSdkImpl.getMockJdk("java 1.4");
  }

  protected ModuleType getModuleType() {
    return StdModuleTypes.JAVA;
  }
}
