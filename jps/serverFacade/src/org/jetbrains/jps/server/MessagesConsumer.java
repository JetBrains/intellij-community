package org.jetbrains.jps.server;

/**
* @author Eugene Zhuravlev
*         Date: 9/10/11
*/
public abstract class MessagesConsumer {
  public void consumeProgressMessage(String message) {
  }
  public void consumeCompilerMessage(String compilerName, String message) {
  }
}
