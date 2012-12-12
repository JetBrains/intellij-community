/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.model.java.impl.compiler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.ex.JpsCompositeElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.java.compiler.JpsCompilerExcludes;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions;
import org.jetbrains.jps.model.java.compiler.ProcessorConfigProfile;

import java.util.*;

/**
 * @author nik
 */
public class JpsJavaCompilerConfigurationImpl extends JpsCompositeElementBase<JpsJavaCompilerConfigurationImpl> implements JpsJavaCompilerConfiguration {
  public static final JpsElementChildRole<JpsJavaCompilerConfiguration> ROLE = JpsElementChildRoleBase.create("compiler configuration");
  private boolean myAddNotNullAssertions = true;
  private boolean myClearOutputDirectoryOnRebuild = true;
  private JpsCompilerExcludes myCompilerExcludes = new JpsCompilerExcludesImpl();
  private List<String> myResourcePatterns = new ArrayList<String>();
  private List<ProcessorConfigProfile> myAnnotationProcessingProfiles = new ArrayList<ProcessorConfigProfile>();
  private ProcessorConfigProfileImpl myDefaultAnnotationProcessingProfile = new ProcessorConfigProfileImpl("Default");
  private String myProjectByteCodeTargetLevel;
  private Map<String, String> myModulesByteCodeTargetLevels = new HashMap<String, String>();
  private Map<String, JpsJavaCompilerOptions> myCompilerOptions = new HashMap<String, JpsJavaCompilerOptions>();
  private String myJavaCompilerId = "Javac";

  public JpsJavaCompilerConfigurationImpl() {
  }

  private JpsJavaCompilerConfigurationImpl(JpsJavaCompilerConfigurationImpl original) {
    super(original);
  }

  @NotNull
  @Override
  public JpsJavaCompilerConfigurationImpl createCopy() {
    return new JpsJavaCompilerConfigurationImpl(this);
  }

  @Override
  public boolean isAddNotNullAssertions() {
    return myAddNotNullAssertions;
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
  public void setClearOutputDirectoryOnRebuild(boolean clearOutputDirectoryOnRebuild) {
    myClearOutputDirectoryOnRebuild = clearOutputDirectoryOnRebuild;
  }

  @NotNull
  @Override
  public JpsCompilerExcludes getCompilerExcludes() {
    return myCompilerExcludes;
  }

  @NotNull
  @Override
  public ProcessorConfigProfile getDefaultAnnotationProcessingConfiguration() {
    return myDefaultAnnotationProcessingProfile;
  }

  @NotNull
  @Override
  public Collection<ProcessorConfigProfile> getAnnotationProcessingConfigurations() {
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
  @Nullable
  public String getByteCodeTargetLevel(String moduleName) {
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

  @NotNull
  @Override
  public String getJavaCompilerId() {
    return myJavaCompilerId;
  }

  @Override
  public void setJavaCompilerId(@NotNull String compiler) {
    myJavaCompilerId = compiler;
  }

  @NotNull
  @Override
  public JpsJavaCompilerOptions getCompilerOptions(@NotNull String compilerId) {
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

  @NotNull
  @Override
  public JpsJavaCompilerOptions getCurrentCompilerOptions() {
    return getCompilerOptions(getJavaCompilerId());
  }

  @Override
  public void setProjectByteCodeTargetLevel(String level) {
    myProjectByteCodeTargetLevel = level;
  }

  @Override
  public ProcessorConfigProfile addAnnotationProcessingProfile() {
    ProcessorConfigProfileImpl profile = new ProcessorConfigProfileImpl("");
    myAnnotationProcessingProfiles.add(profile);
    return profile;
  }
}
