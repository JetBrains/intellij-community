// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.java.impl.compiler;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.compiler.ProcessorConfigProfile;

import java.util.*;

public final class ProcessorConfigProfileImpl implements ProcessorConfigProfile {
  private String myName = "";
  private boolean myEnabled = false;
  private boolean myObtainProcessorsFromClasspath = true;
  private String myProcessorPath = "";
  private boolean myUseProcessorModulePath = false;
  private final Set<String> myProcessors = new LinkedHashSet<>(1); // empty list means all discovered
  private final Map<String, String> myProcessorOptions = new HashMap<>(1); // key=value map of options
  private String myGeneratedProductionDirectoryName = DEFAULT_PRODUCTION_DIR_NAME;
  private String myGeneratedTestsDirectoryName = DEFAULT_TESTS_DIR_NAME;
  private boolean myOutputRelativeToContentRoot = false;
  private boolean myIsProcOnly = false;

  private final Set<String> myModuleNames = new HashSet<>(1);

  /**
   * Creates a new empty profile with the given name. 
   * Use {@link com.intellij.compiler.CompilerConfiguration#addNewProcessorProfile(String)} to add a new profile in plugins.
   */
  public ProcessorConfigProfileImpl(String name) {
    myName = name;
  }

  /**
   * Creates a new profile copied from the given one. 
   * Use {@link com.intellij.compiler.CompilerConfiguration#addNewProcessorProfile(String)} and {@link #initFrom(ProcessorConfigProfile)}
   * in plugins.
   */
  public ProcessorConfigProfileImpl(ProcessorConfigProfile profile) {
    initFrom(profile);
  }

  @ApiStatus.Internal
  @Override
  public void initFrom(ProcessorConfigProfile other) {
    myName = other.getName();
    myEnabled = other.isEnabled();
    myIsProcOnly = other.isProcOnly();
    myObtainProcessorsFromClasspath = other.isObtainProcessorsFromClasspath();
    myProcessorPath = other.getProcessorPath();
    myUseProcessorModulePath = other.isUseProcessorModulePath();
    myProcessors.clear();
    myProcessors.addAll(other.getProcessors());
    myProcessorOptions.clear();
    myProcessorOptions.putAll(other.getProcessorOptions());
    myGeneratedProductionDirectoryName = other.getGeneratedSourcesDirectoryName(false);
    myGeneratedTestsDirectoryName = other.getGeneratedSourcesDirectoryName(true);
    myOutputRelativeToContentRoot = other.isOutputRelativeToContentRoot();
    myModuleNames.clear();
    myModuleNames.addAll(other.getModuleNames());
  }

  @ApiStatus.Internal
  @Override
  public String getName() {
    return myName;
  }

  @ApiStatus.Internal
  @Override
  public void setName(String name) {
    myName = name;
  }

  @ApiStatus.Internal
  @Override
  public boolean isEnabled() {
    return myEnabled;
  }

  @ApiStatus.Internal
  @Override
  public void setEnabled(boolean enabled) {
    myEnabled = enabled;
  }

  @ApiStatus.Internal
  @Override
  public @NotNull String getProcessorPath() {
    return myProcessorPath;
  }

  @ApiStatus.Internal
  @Override
  public void setProcessorPath(@Nullable String processorPath) {
    myProcessorPath = processorPath != null? processorPath : "";
  }

  @ApiStatus.Internal
  @Override
  public void setUseProcessorModulePath(boolean useModulePath) {
    myUseProcessorModulePath = useModulePath;
  }

  @ApiStatus.Internal
  @Override
  public boolean isUseProcessorModulePath() {
    return myUseProcessorModulePath;
  }

  @ApiStatus.Internal
  @Override
  public boolean isObtainProcessorsFromClasspath() {
    return myObtainProcessorsFromClasspath;
  }

  @ApiStatus.Internal
  @Override
  public void setObtainProcessorsFromClasspath(boolean value) {
    myObtainProcessorsFromClasspath = value;
  }

  @ApiStatus.Internal
  @Override
  public @NotNull String getGeneratedSourcesDirectoryName(boolean forTests) {
    return forTests? myGeneratedTestsDirectoryName : myGeneratedProductionDirectoryName;
  }

  @ApiStatus.Internal
  @Override
  public void setGeneratedSourcesDirectoryName(@Nullable String name, boolean forTests) {
    if (forTests) {
      myGeneratedTestsDirectoryName = name != null? name.trim() : DEFAULT_TESTS_DIR_NAME;
    }
    else {
      myGeneratedProductionDirectoryName = name != null? name.trim() : DEFAULT_PRODUCTION_DIR_NAME;
    }
  }

  @ApiStatus.Internal
  @Override
  public boolean isOutputRelativeToContentRoot() {
    return myOutputRelativeToContentRoot;
  }

  @ApiStatus.Internal
  @Override
  public void setOutputRelativeToContentRoot(boolean relativeToContent) {
    myOutputRelativeToContentRoot = relativeToContent;
  }

  @ApiStatus.Internal
  @Override
  public boolean isProcOnly() {
    return myIsProcOnly;
  }

  @ApiStatus.Internal
  @Override
  public void setProcOnly(boolean value) {
    myIsProcOnly = value;
  }

  @ApiStatus.Internal
  @Override
  public @NotNull Set<String> getModuleNames() {
    return myModuleNames;
  }

  @ApiStatus.Internal
  @Override
  public boolean addModuleName(String name) {
    return myModuleNames.add(name);
  }

  @ApiStatus.Internal
  @Override
  public boolean addModuleNames(Collection<String> names) {
    return myModuleNames.addAll(names);
  }

  @ApiStatus.Internal
  @Override
  public boolean removeModuleName(String name) {
    return myModuleNames.remove(name);
  }

  @ApiStatus.Internal
  @Override
  public boolean removeModuleNames(Collection<String> names) {
    return myModuleNames.removeAll(names);
  }

  @ApiStatus.Internal
  @Override
  public void clearModuleNames() {
    myModuleNames.clear();
  }

  @ApiStatus.Internal
  @Override
  public void clearProcessors() {
    myProcessors.clear();
  }

  @ApiStatus.Internal
  @Override
  public boolean addProcessor(String processor) {
    return myProcessors.add(processor);
  }

  @ApiStatus.Internal
  @Override
  public boolean removeProcessor(String processor) {
    return myProcessors.remove(processor);
  }

  @ApiStatus.Internal
  @Override
  public @NotNull Set<String> getProcessors() {
    return Collections.unmodifiableSet(myProcessors);
  }

  @ApiStatus.Internal
  @Override
  public @NotNull Map<String, String> getProcessorOptions() {
    return Collections.unmodifiableMap(myProcessorOptions);
  }

  @ApiStatus.Internal
  @Override
  public String setOption(String key, String value) {
    return myProcessorOptions.put(key, value);
  }

  @ApiStatus.Internal
  @Override
  public @Nullable String getOption(String key) {
    return myProcessorOptions.get(key);
  }

  @ApiStatus.Internal
  @Override
  public void clearProcessorOptions() {
    myProcessorOptions.clear();
  }

  @ApiStatus.Internal
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ProcessorConfigProfileImpl profile = (ProcessorConfigProfileImpl)o;

    if (myEnabled != profile.myEnabled) return false;
    if (myIsProcOnly != profile.myIsProcOnly) return false;
    if (myObtainProcessorsFromClasspath != profile.myObtainProcessorsFromClasspath) return false;
    if (myGeneratedProductionDirectoryName != null
        ? !myGeneratedProductionDirectoryName.equals(profile.myGeneratedProductionDirectoryName)
        : profile.myGeneratedProductionDirectoryName != null) {
      return false;
    }
    if (myGeneratedTestsDirectoryName != null
        ? !myGeneratedTestsDirectoryName.equals(profile.myGeneratedTestsDirectoryName)
        : profile.myGeneratedTestsDirectoryName != null) {
      return false;
    }
    if (myOutputRelativeToContentRoot != profile.myOutputRelativeToContentRoot) return false;
    if (myUseProcessorModulePath != profile.myUseProcessorModulePath) return false;
    if (!myModuleNames.equals(profile.myModuleNames)) return false;
    if (!myProcessorOptions.equals(profile.myProcessorOptions)) return false;
    if (myProcessorPath != null ? !myProcessorPath.equals(profile.myProcessorPath) : profile.myProcessorPath != null) return false;
    if (!myProcessors.equals(profile.myProcessors)) return false;
    if (!myName.equals(profile.myName)) return false;

    return true;
  }

  @ApiStatus.Internal
  @Override
  public int hashCode() {
    int result = myName.hashCode();
    result = 31 * result + (myEnabled ? 1 : 0);
    result = 31 * result + (myIsProcOnly ? 1 : 0);
    result = 31 * result + (myObtainProcessorsFromClasspath ? 1 : 0);
    result = 31 * result + (myProcessorPath != null ? myProcessorPath.hashCode() : 0);
    result = 31 * result + myProcessors.hashCode();
    result = 31 * result + myProcessorOptions.hashCode();
    result = 31 * result + (myGeneratedProductionDirectoryName != null ? myGeneratedProductionDirectoryName.hashCode() : 0);
    result = 31 * result + (myGeneratedTestsDirectoryName != null ? myGeneratedTestsDirectoryName.hashCode() : 0);
    result = 31 * result + (myOutputRelativeToContentRoot ? 1 : 0);
    result = 31 * result + (myUseProcessorModulePath ? 1 : 0);
    result = 31 * result + myModuleNames.hashCode();
    return result;
  }

  @ApiStatus.Internal
  @Override
  public String toString() {
    return myName;
  }
}

