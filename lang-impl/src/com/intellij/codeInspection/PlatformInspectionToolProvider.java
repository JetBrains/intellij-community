package com.intellij.codeInspection;

/**
 * @author yole
 */
public class PlatformInspectionToolProvider implements InspectionToolProvider {
  public Class[] getInspectionClasses() {
    return new Class[] { SyntaxErrorInspection.class };
  }
}
