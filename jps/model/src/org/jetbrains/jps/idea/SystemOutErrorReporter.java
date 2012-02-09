package org.jetbrains.jps.idea;

public class SystemOutErrorReporter implements ProjectLoadingErrorReporter {
  private boolean myFailOnError;

  public SystemOutErrorReporter(boolean failOnError) {
    myFailOnError = failOnError;
  }

  public void error(String message) {
    if (myFailOnError) {
      throw new CannotLoadProjectException(message);
    }
    System.out.println("error: " + message);
  }

  public void warning(String message) {
    System.out.println("warn: " + message);
  }

  public void info(String message) {
    System.out.println("info: " + message);
  }
}
