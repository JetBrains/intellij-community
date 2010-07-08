package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;

/**
 * @author ven
 */
public abstract class LightQuickFix15TestCase extends LightQuickFixTestCase {

  protected Sdk getProjectJDK() {
    return JavaSdkImpl.getMockJdk17("java 1.5");
  }

}
