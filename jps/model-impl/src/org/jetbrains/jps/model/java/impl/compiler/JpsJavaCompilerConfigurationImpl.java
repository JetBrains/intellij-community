// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.java.impl.compiler;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.ex.JpsCompositeElementBase;
import org.jetbrains.jps.model.ex.JpsFactoryElementChildRoleBase;
import org.jetbrains.jps.model.java.compiler.*;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.*;

@ApiStatus.Internal
public class JpsJavaCompilerConfigurationImpl extends JpsCompositeElementBase<JpsJavaCompilerConfigurationImpl> implements JpsJavaCompilerConfiguration {
  public static final JpsFactoryElementChildRoleBase<JpsJavaCompilerConfiguration> ROLE = JpsFactoryElementChildRoleBase.create("compiler configuration", () -> new JpsJavaCompilerConfigurationImpl());
  private boolean myAddNotNullAssertions = true;
  private List<String> myNotNullAnnotations = Collections.singletonList(NotNull.class.getName());
  private boolean myClearOutputDirectoryOnRebuild = true;
  private final JpsCompilerExcludes myCompilerExcludes = new JpsCompilerExcludesImpl();
  private final JpsCompilerExcludes myValidationExcludes = new JpsCompilerExcludesImpl();
  private final List<String> myResourcePatterns = new ArrayList<>();
  private final List<ProcessorConfigProfile> myAnnotationProcessingProfiles = new ArrayList<>();
  private final ProcessorConfigProfileImpl myDefaultAnnotationProcessingProfile = new ProcessorConfigProfileImpl("Default");
  private boolean myUseReleaseOption = true;
  private String myProjectByteCodeTargetLevel;
  private final Map<String, String> myModulesByteCodeTargetLevels = new HashMap<>();
  private final Map<String, JpsJavaCompilerOptions> myCompilerOptions = new HashMap<>();
  private String myJavaCompilerId = "Javac";
  private Map<JpsModule, ProcessorConfigProfile> myAnnotationProcessingProfileMap;
  private ResourcePatterns myCompiledPatterns;
  private JpsValidationConfiguration myValidationConfiguration = new JpsValidationConfigurationImpl(false, Collections.emptySet());

  public JpsJavaCompilerConfigurationImpl() {
  }

  private JpsJavaCompilerConfigurationImpl(JpsJavaCompilerConfigurationImpl original) {
    super(original);
  }

  @Override
  public @NotNull JpsJavaCompilerConfigurationImpl createCopy() {
    return new JpsJavaCompilerConfigurationImpl(this);
  }

  @Override
  public boolean isAddNotNullAssertions() {
    return myAddNotNullAssertions;
  }

  @Override
  public List<String> getNotNullAnnotations() {
    return myNotNullAnnotations;
  }

  @Override
  public boolean isClearOutputDirectoryOnRebuild() {
    return myClearOutputDirectoryOnRebuild;
  }

  @Override
  public void setAddNotNullAssertions(boolean addNotNullAssertions) {
    myAddNotNullAssertions = addNotNullAssertions;
  }

  @Override
  public void setNotNullAnnotations(List<String> notNullAnnotations) {
    myNotNullAnnotations = Collections.unmodifiableList(notNullAnnotations);
  }

  @Override
  public void setClearOutputDirectoryOnRebuild(boolean clearOutputDirectoryOnRebuild) {
    myClearOutputDirectoryOnRebuild = clearOutputDirectoryOnRebuild;
  }

  @Override
  public @NotNull JpsCompilerExcludes getCompilerExcludes() {
    return myCompilerExcludes;
  }

  @Override
  public @NotNull JpsCompilerExcludes getValidationExcludes() {
    return myValidationExcludes;
  }

  @Override
  public @NotNull JpsValidationConfiguration getValidationConfiguration() {
    return myValidationConfiguration;
  }

  @Override
  public void setValidationConfiguration(boolean validateOnBuild, @NotNull Set<String> disabledValidators) {
    myValidationConfiguration = new JpsValidationConfigurationImpl(validateOnBuild, disabledValidators);
  }

  @Override
  public @NotNull ProcessorConfigProfile getDefaultAnnotationProcessingProfile() {
    return myDefaultAnnotationProcessingProfile;
  }

  @Override
  public @NotNull Collection<ProcessorConfigProfile> getAnnotationProcessingProfiles() {
    return myAnnotationProcessingProfiles;
  }

  @Override
  public void addResourcePattern(String pattern) {
    myResourcePatterns.add(pattern);
  }

  @Override
  public List<String> getResourcePatterns() {
    return myResourcePatterns;
  }

  @Override
  public boolean isResourceFile(@NotNull File file, @NotNull File srcRoot) {
    ResourcePatterns patterns = myCompiledPatterns;
    if (patterns == null) {
      myCompiledPatterns = patterns = new ResourcePatterns(this);
    }
    return patterns.isResourceFile(file, srcRoot);
  }

  @Override
  public @Nullable String getByteCodeTargetLevel(String moduleName) {
    String level = myModulesByteCodeTargetLevels.get(moduleName);
    if (level != null) {
      return level.isEmpty() ? null : level;
    }
    return myProjectByteCodeTargetLevel;
  }

  @Override
  public void setModuleByteCodeTargetLevel(String moduleName, String level) {
    myModulesByteCodeTargetLevels.put(moduleName, level);
  }

  @Override
  public @NotNull String getJavaCompilerId() {
    return myJavaCompilerId;
  }

  @Override
  public void setJavaCompilerId(@NotNull String compiler) {
    myJavaCompilerId = compiler;
  }

  @Override
  public @NotNull JpsJavaCompilerOptions getCompilerOptions(@NotNull String compilerId) {
    JpsJavaCompilerOptions options = myCompilerOptions.get(compilerId);
    if (options == null) {
      options = new JpsJavaCompilerOptions();
      myCompilerOptions.put(compilerId, options);
    }
    return options;
  }

  @Override
  public void setCompilerOptions(@NotNull String compilerId, @NotNull JpsJavaCompilerOptions options) {
    myCompilerOptions.put(compilerId, options);
  }

  @Override
  public @NotNull JpsJavaCompilerOptions getCurrentCompilerOptions() {
    return getCompilerOptions(getJavaCompilerId());
  }

  @Override
  public void setProjectByteCodeTargetLevel(String level) {
    myProjectByteCodeTargetLevel = level;
  }

  @Override
  public boolean useReleaseOption() {
    return myUseReleaseOption;
  }

  @Override
  public void setUseReleaseOption(boolean useReleaseOption) {
    myUseReleaseOption = useReleaseOption;
  }

  @Override
  public ProcessorConfigProfile addAnnotationProcessingProfile() {
    ProcessorConfigProfileImpl profile = new ProcessorConfigProfileImpl("");
    myAnnotationProcessingProfiles.add(profile);
    return profile;
  }

  @Override
  public @NotNull ProcessorConfigProfile getAnnotationProcessingProfile(JpsModule module) {
    Map<JpsModule, ProcessorConfigProfile> map = myAnnotationProcessingProfileMap;
    if (map == null) {
      map = new HashMap<>();
      for (ProcessorConfigProfile profile : getAnnotationProcessingProfiles()) {
        for (String name : profile.getModuleNames()) {
          final JpsModule mod = module.getProject().findModuleByName(name);
          if (mod != null) {
            map.put(mod, profile);
          }
        }
      }
      myAnnotationProcessingProfileMap = map;
    }
    final ProcessorConfigProfile profile = map.get(module);
    return profile != null? profile : getDefaultAnnotationProcessingProfile();
  }
}
