package org.jetbrains.jps.gant;

/**
 * @author nik
 */
public interface BuildInfoPrinter {

  void printProgressMessage(JpsGantProjectBuilder project, String message);

  void printCompilationErrors(JpsGantProjectBuilder project, String compilerName, String messages);

  void printCompilationFinish(JpsGantProjectBuilder project, String compilerName);

  void printCompilationStart(JpsGantProjectBuilder project, String compilerName);
}
