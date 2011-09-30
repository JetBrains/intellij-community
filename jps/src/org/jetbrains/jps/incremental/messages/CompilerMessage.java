package org.jetbrains.jps.incremental.messages;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/29/11
 */
public class CompilerMessage extends BuildMessage {

  private final String myCompilerName;

  public CompilerMessage(String compilerName, String messageText) {
    super(messageText);
    myCompilerName = compilerName;
  }

  public String getCompilerName() {
    return myCompilerName;
  }

}
