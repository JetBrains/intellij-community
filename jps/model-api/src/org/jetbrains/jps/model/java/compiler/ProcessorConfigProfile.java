package org.jetbrains.jps.model.java.compiler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

/**
 * @author nik
 */
public interface ProcessorConfigProfile extends AnnotationProcessingConfiguration {
  void initFrom(ProcessorConfigProfile other);

  String getName();

  void setName(String name);

  void setEnabled(boolean enabled);

  void setProcessorPath(@Nullable String processorPath);

  void setObtainProcessorsFromClasspath(boolean value);

  void setGeneratedSourcesDirectoryName(@Nullable String generatedSourcesDirectoryName, boolean forTests);

  @NotNull
  Set<String> getModuleNames();

  boolean addModuleName(String name);

  boolean addModuleNames(Collection<String> names);

  boolean removeModuleName(String name);

  boolean removeModuleNames(Collection<String> names);

  void clearModuleNames();

  void clearProcessors();

  boolean addProcessor(String processor);

  boolean removeProcessor(String processor);

  String setOption(String key, String value);

  @Nullable
  String getOption(String key);

  void clearProcessorOptions();
}
