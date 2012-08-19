package org.jetbrains.jps.gant;

/**
 * @author nik
 */
public class DefaultBuildInfoPrinter implements BuildInfoPrinter {
  @Override
  public void printProgressMessage(JpsGantProjectBuilder project, String message) {
    project.info(message);
  }

  @Override
  public void printCompilationErrors(JpsGantProjectBuilder project, String compilerName, String messages) {
    project.error(messages);
  }

  @Override
  public void printCompilationFinish(JpsGantProjectBuilder project, String compilerName) {
  }

  @Override
  public void printCompilationStart(JpsGantProjectBuilder project, String compilerName) {
  }
}
