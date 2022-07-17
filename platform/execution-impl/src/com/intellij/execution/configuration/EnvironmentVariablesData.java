// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configuration;

import com.google.common.collect.ImmutableMap;
import com.intellij.execution.configurations.GeneralCommandLine;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Holds environment variables configuration:
 * <ul>
 * <li>list of user-defined environment variables</li>
 * <li>boolean flag - whether to pass system environment</li>
 * </ul>
 * Instances of this class are immutable objects, so it can be safely passed across threads.
 */
public final class EnvironmentVariablesData {
  public static final EnvironmentVariablesData DEFAULT = new EnvironmentVariablesData(Map.of(), true, null);

  private static final String ENVS = "envs";
  private static final String PASS_PARENT_ENVS = "pass-parent-envs";
  private static final String ENV = EnvironmentVariablesComponent.ENV;
  private static final String NAME = EnvironmentVariablesComponent.NAME;
  private static final String VALUE = EnvironmentVariablesComponent.VALUE;

  private final Map<String, String> myEnvs;
  private final String myEnvironmentFile;
  private final boolean myPassParentEnvs;

  private EnvironmentVariablesData(@NotNull Map<String, String> envs, boolean passParentEnvs, @Nullable String environmentFile) {
    // insertion order must be preserved - Map.copyOf cannot be used here
    myEnvs = envs.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(envs));
    myPassParentEnvs = passParentEnvs;
    myEnvironmentFile = environmentFile;
  }

  /**
   * @return immutable Map instance containing user-defined environment variables (iteration order is reliable user-specified)
   */
  public @NotNull Map<String, String> getEnvs() {
    return myEnvs;
  }

  @Nullable
  public String getEnvironmentFile() {
    return myEnvironmentFile;
  }

  public boolean isPassParentEnvs() {
    return myPassParentEnvs;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    EnvironmentVariablesData data = (EnvironmentVariablesData)o;
    return myPassParentEnvs == data.myPassParentEnvs && myEnvs.equals(data.myEnvs) && Objects.equals(myEnvironmentFile, data.myEnvironmentFile);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = myEnvs.hashCode();
    result = prime * result + (myPassParentEnvs ? 1 : 0);
    if (myEnvironmentFile != null) {
      result = prime * result + myEnvironmentFile.hashCode();
    }
    return result;
  }

  @Override
  public String toString() {
    return "envs=" + myEnvs + ", passParentEnvs=" + myPassParentEnvs + ", environmentFile=" + myEnvironmentFile;
  }

  public static @NotNull EnvironmentVariablesData readExternal(@NotNull Element element) {
    Element envsElement = element.getChild(ENVS);
    if (envsElement == null) {
      return DEFAULT;
    }
    Map<String, String> envs = Map.of();
    String passParentEnvsStr = envsElement.getAttributeValue(PASS_PARENT_ENVS);
    boolean passParentEnvs = passParentEnvsStr == null || Boolean.parseBoolean(passParentEnvsStr);
    for (Element envElement : envsElement.getChildren(ENV)) {
      String envName = envElement.getAttributeValue(NAME);
      String envValue = envElement.getAttributeValue(VALUE);
      if (envName != null && envValue != null) {
        if (envs.isEmpty()) {
          envs = new LinkedHashMap<>();
        }
        envs.put(envName, envValue);
      }
    }
    return create(envs, passParentEnvs);
  }

  public void writeExternalNoDefault(@NotNull Element parent) {
    if (!DEFAULT.equals(this)) {
      writeExternal(parent);
    }
  }

  public void writeExternal(@NotNull Element parent) {
    Element envsElement = new Element(ENVS);
    if (!myPassParentEnvs) {
      // Avoid writing pass-parent-envs="true" to minimize changes in xml comparing it to xml written by
      // com.intellij.execution.configuration.EnvironmentVariablesComponent.writeExternal
      envsElement.setAttribute(PASS_PARENT_ENVS, Boolean.FALSE.toString());
    }
    for (Map.Entry<String, String> entry : myEnvs.entrySet()) {
      envsElement.addContent(new Element(ENV).setAttribute(NAME, entry.getKey()).setAttribute(VALUE, entry.getValue()));
    }
    parent.addContent(envsElement);
  }

  public void configureCommandLine(@NotNull GeneralCommandLine commandLine, boolean consoleParentEnvs) {
    commandLine.withParentEnvironmentType(!myPassParentEnvs ? GeneralCommandLine.ParentEnvironmentType.NONE :
                                          consoleParentEnvs ? GeneralCommandLine.ParentEnvironmentType.CONSOLE
                                                            : GeneralCommandLine.ParentEnvironmentType.SYSTEM);
    commandLine.withEnvironment(myEnvs);
  }

  public static @NotNull EnvironmentVariablesData create(@NotNull Map<String, String> envs, boolean passParentEnvs) {
    return create(envs, passParentEnvs, null);
  }

  /**
   * @param envs Map instance containing user-defined environment variables
   *             (iteration order should be reliable user-specified, like {@link LinkedHashMap} or {@link ImmutableMap})
   * @param passParentEnvs true if system environment should be passed
   * @param environmentFile path to .env file
   */
  public static @NotNull EnvironmentVariablesData create(@NotNull Map<String, String> envs,
                                                         boolean passParentEnvs,
                                                         @Nullable String environmentFile) {
    if (passParentEnvs && envs.isEmpty() && environmentFile == null) {
      return DEFAULT;
    }
    return new EnvironmentVariablesData(envs, passParentEnvs, environmentFile);
  }

  public @NotNull EnvironmentVariablesData with(@NotNull Map<String, String> envs) {
    return create(envs, myPassParentEnvs, myEnvironmentFile);
  }

  public @NotNull EnvironmentVariablesData with(boolean passParentEnvs) {
    return passParentEnvs == myPassParentEnvs ? this : create(myEnvs, passParentEnvs, myEnvironmentFile);
  }

  public @NotNull EnvironmentVariablesData with(@Nullable String environmentFile) {
    return Objects.equals(environmentFile, myEnvironmentFile) ? this : create(myEnvs, myPassParentEnvs, environmentFile);
  }
}