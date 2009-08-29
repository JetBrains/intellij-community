package com.intellij.psi.scope;

/**
 * @author yole
 */
public class JavaScopeProcessorEvent implements PsiScopeProcessor.Event {
  private JavaScopeProcessorEvent() {
  }

  public static final JavaScopeProcessorEvent START_STATIC = new JavaScopeProcessorEvent();
  public static final JavaScopeProcessorEvent CHANGE_LEVEL = new JavaScopeProcessorEvent();
  public static final JavaScopeProcessorEvent SET_CURRENT_FILE_CONTEXT = new JavaScopeProcessorEvent();
}
