package org.jetbrains.jps.model.java.compiler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;

import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public interface JpsJavaCompilerConfiguration extends JpsElement {
  boolean isAddNotNullAssertions();
  void setAddNotNullAssertions(boolean addNotNullAssertions);

  boolean isClearOutputDirectoryOnRebuild();
  void setClearOutputDirectoryOnRebuild(boolean clearOutputDirectoryOnRebuild);

  @NotNull
  JpsCompilerExcludes getCompilerExcludes();

  @NotNull
  ProcessorConfigProfile getDefaultAnnotationProcessingConfiguration();
  ProcessorConfigProfile addAnnotationProcessingProfile();
  @NotNull
  Collection<ProcessorConfigProfile> getAnnotationProcessingConfigurations();

  void addResourcePattern(String pattern);
  List<String> getResourcePatterns();


  @Nullable
  String getByteCodeTargetLevel(String moduleName);

  void setProjectByteCodeTargetLevel(String level);
  void setModuleByteCodeTargetLevel(String moduleName, String level);

  @NotNull
  String getJavaCompilerId();
  void setJavaCompilerId(@NotNull String compiler);

  @NotNull
  JpsJavaCompilerOptions getCompilerOptions(@NotNull String compilerId);
  void setCompilerOptions(@NotNull String compilerId, @NotNull JpsJavaCompilerOptions options);

  @NotNull
  JpsJavaCompilerOptions getCurrentCompilerOptions();

}
