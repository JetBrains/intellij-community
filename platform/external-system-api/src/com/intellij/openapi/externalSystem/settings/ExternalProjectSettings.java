// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.settings;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Holds settings specific to a particular project imported from an external system.
 *
 * @author Denis Zhdanov
 */
public abstract class ExternalProjectSettings implements Comparable<ExternalProjectSettings>, Cloneable {

  private static final Logger LOG = Logger.getInstance(ExternalProjectSettings.class);

  private String myExternalProjectPath;
  @Nullable private Set<String> myModules = new HashSet<>();

  @NotNull
  public Set<String> getModules() {
    return myModules == null ? Collections.emptySet() : myModules;
  }

  public void setModules(@Nullable Set<String> modules) {
    this.myModules = modules;
  }

  private boolean myUseQualifiedModuleNames = true;

  /**
   * @deprecated left for settings backward-compatibility
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  private boolean myCreateEmptyContentRootDirectories;

  // Used to gradually migrate new project to the new defaults.
  public void setupNewProjectDefault() {
    myUseQualifiedModuleNames = true;
  }

  public String getExternalProjectPath() {
    return myExternalProjectPath;
  }

  public void setExternalProjectPath(@NotNull String externalProjectPath) {
    myExternalProjectPath = externalProjectPath;
  }

  /**
   * @deprecated Auto-import cannot be disabled
   * @see com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTracker for details
   */
  @Transient
  @Deprecated(forRemoval = true)
  public void setUseAutoImport(@SuppressWarnings("unused") boolean useAutoImport) {
    LOG.warn(new Throwable("Auto-import cannot be disabled"));
  }

  /**
   * @deprecated left for settings backward-compatibility
   */
  @Deprecated(forRemoval = true)
  public boolean isCreateEmptyContentRootDirectories() {
    return myCreateEmptyContentRootDirectories;
  }

  /**
   * @deprecated left for settings backward-compatibility
   */
  @Deprecated(forRemoval = true)
  public void setCreateEmptyContentRootDirectories(boolean createEmptyContentRootDirectories) {
    myCreateEmptyContentRootDirectories = createEmptyContentRootDirectories;
  }

  public boolean isUseQualifiedModuleNames() {
    return myUseQualifiedModuleNames;
  }

  public void setUseQualifiedModuleNames(boolean useQualifiedModuleNames) {
    myUseQualifiedModuleNames = useQualifiedModuleNames;
  }

  @Override
  public int compareTo(@NotNull ExternalProjectSettings that) {
    return Comparing.compare(myExternalProjectPath, that.myExternalProjectPath);
  }

  @Override
  public int hashCode() {
    return myExternalProjectPath != null ? myExternalProjectPath.hashCode() : 0;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ExternalProjectSettings that = (ExternalProjectSettings)o;

    return myExternalProjectPath == null ? that.myExternalProjectPath == null : myExternalProjectPath.equals(that.myExternalProjectPath);
  }

  @Override
  public String toString() {
    return myExternalProjectPath;
  }

  @Override
  @NotNull
  public abstract ExternalProjectSettings clone();

  protected void copyTo(@NotNull ExternalProjectSettings receiver) {
    receiver.myExternalProjectPath = myExternalProjectPath;
    receiver.myModules = myModules != null ? new HashSet<>(myModules) : new HashSet<>();
    receiver.myCreateEmptyContentRootDirectories = myCreateEmptyContentRootDirectories;
    receiver.myUseQualifiedModuleNames = myUseQualifiedModuleNames;
  }
}
