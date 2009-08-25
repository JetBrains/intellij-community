package com.intellij.testFramework;

import com.intellij.psi.impl.JavaPsiFacadeEx;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;

/**
 * A TestCase for single PsiFile being opened in Editor conversion. See configureXXX and checkResultXXX method docs.
 */
public class LightCodeInsightTestCase extends LightPlatformCodeInsightTestCase {
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
