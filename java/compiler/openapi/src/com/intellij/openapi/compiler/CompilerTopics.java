package com.intellij.openapi.compiler;

import com.intellij.util.messages.Topic;

/**
 * @author yole
 */
public class CompilerTopics {
  public static final Topic<CompilationStatusListener> COMPILATION_STATUS = new Topic<CompilationStatusListener>("compilation status", CompilationStatusListener.class);

  private CompilerTopics() {
  }
}
