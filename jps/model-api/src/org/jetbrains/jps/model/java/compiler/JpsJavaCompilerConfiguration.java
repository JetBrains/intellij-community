package org.jetbrains.jps.model.java.compiler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.module.JpsModule;

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
  ProcessorConfigProfile getDefaultAnnotationProcessingProfile();
  ProcessorConfigProfile addAnnotationProcessingProfile();

  /**
   * @return a list of currently configured profiles excluding default one
   */
  @NotNull
  Collection<ProcessorConfigProfile> getAnnotationProcessingProfiles();

  /**
   * @param module
   * @return annotation profile with which the given module is associated
   */
  @NotNull
  ProcessorConfigProfile getAnnotationProcessingProfile(JpsModule module);

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
