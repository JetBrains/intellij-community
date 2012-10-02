package org.jetbrains.jps.model.java.compiler;

import org.jetbrains.annotations.TestOnly;

/**
 * @author nik
 */
public class JpsJavaCompilerOptions {
  public boolean DEBUGGING_INFO = true;
  public boolean GENERATE_NO_WARNINGS = false;
  public boolean DEPRECATION = true;
  public String ADDITIONAL_OPTIONS_STRING = "";
  public int MAXIMUM_HEAP_SIZE = 128;

  private boolean myTestsUseExternalCompiler = false;

  @TestOnly
  public boolean isTestsUseExternalCompiler() {
    return myTestsUseExternalCompiler;
  }

  @TestOnly
  public void setTestsUseExternalCompiler(boolean testsUseExternalCompiler) {
    myTestsUseExternalCompiler = testsUseExternalCompiler;
  }
}
