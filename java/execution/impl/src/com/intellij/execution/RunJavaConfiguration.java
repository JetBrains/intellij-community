package com.intellij.execution;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

public interface RunJavaConfiguration {
  int VM_PARAMETERS_PROPERTY = 0;
  int PROGRAM_PARAMETERS_PROPERTY = 1;
  int WORKING_DIRECTORY_PROPERTY = 2;

  void setProperty(int property, String value);
  String getProperty(int property);

  Project getProject();

  boolean isAlternativeJrePathEnabled();

  void setAlternativeJrePathEnabled(boolean enabled);

  String getAlternativeJrePath();

  void setAlternativeJrePath(String ALTERNATIVE_JRE_PATH);

  @Nullable
  String getRunClass();

  @Nullable
  String getPackage();

}
