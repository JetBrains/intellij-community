package com.intellij.compiler;

import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.compiler.impl.javaCompiler.javac.JavacSettings;

import java.io.File;

/**
 * @author Eugene Zhuravlev
 *         Date: Feb 26, 2004
 */
public abstract class Jdk15CompilerTestCase extends CompilerTestCase{
  private boolean myUseExternalCompiler;
  private String myAdditionalOptions;

  protected Jdk15CompilerTestCase(String groupName) {
    super(groupName);
  }

  protected ProjectJdk getTestProjectJdk() {
    return JavaSdkImpl.getMockJdk15("java 1.5");
  }

  protected void setUp() throws Exception {
    final String compilerHome = System.getProperty(CompilerConfiguration.TESTS_EXTERNAL_COMPILER_HOME_PROPERTY_NAME, null);
    if (compilerHome == null || compilerHome.length() == 0) {
      throw new Exception("Property \"" + CompilerConfiguration.TESTS_EXTERNAL_COMPILER_HOME_PROPERTY_NAME + "\" must be specified in order to run JDK 1.5 compiler tests");
    }
    if (!new File(compilerHome).exists()) {
      throw new Exception("The home directory for tests external compiler does not exist: " + compilerHome);
    }

    super.setUp();

    final JavacSettings javacSettings = JavacSettings.getInstance(myProject);
    myUseExternalCompiler = javacSettings.isTestsUseExternalCompiler();
    myAdditionalOptions = javacSettings.ADDITIONAL_OPTIONS_STRING;

    javacSettings.setTestsUseExternalCompiler(true);
    javacSettings.ADDITIONAL_OPTIONS_STRING = "-source 1.5";
  }

  protected void tearDown() throws Exception {
    final JavacSettings javacSettings = JavacSettings.getInstance(myProject);
    javacSettings.setTestsUseExternalCompiler(myUseExternalCompiler);
    javacSettings.ADDITIONAL_OPTIONS_STRING = myAdditionalOptions;
    super.tearDown();
  }
}
