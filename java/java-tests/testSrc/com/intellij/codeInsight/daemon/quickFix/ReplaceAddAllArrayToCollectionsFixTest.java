/*
 * User: anna
 * Date: 21-Mar-2008
 */
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;

public class ReplaceAddAllArrayToCollectionsFixTest extends LightQuickFixTestCase {
   public void test() throws Exception {
     doAllTests();
   }

  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/replaceAddAllArrayToCollections";
  }

  @Override
  protected Sdk getProjectJDK() {
    return JavaSdkImpl.getMockJdk15("java 1.5");
  }
}