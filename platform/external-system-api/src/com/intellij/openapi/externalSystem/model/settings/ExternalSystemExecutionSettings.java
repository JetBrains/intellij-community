// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model.settings;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.*;

/**
 * Holds execution settings of particular invocation of an external system.
 * E.g. task running or project importing.
 */
public class ExternalSystemExecutionSettings implements Serializable, UserDataHolder {

  public static final String REMOTE_PROCESS_IDLE_TTL_IN_MS_KEY = "external.system.remote.process.idle.ttl.ms";
  private static final int DEFAULT_REMOTE_PROCESS_TTL_MS = -1;

  public static final Key<Boolean> DEBUG_SERVER_PROCESS_KEY = Key.create("DEBUG_SERVER_PROCESS");

  private static final long serialVersionUID = 1L;

  private long myRemoteProcessIdleTtlInMs;
  private boolean myVerboseProcessing;
  private final @NotNull List<String> myJvmArguments;
  private final @NotNull List<String> myArguments;
  private final @NotNull Map<String, String> myEnv;
  private boolean myPassParentEnvs;

  private final transient @NotNull UserDataHolderBase myUserData = new UserDataHolderBase();

  public ExternalSystemExecutionSettings() {
    myRemoteProcessIdleTtlInMs = SystemProperties.getIntProperty(REMOTE_PROCESS_IDLE_TTL_IN_MS_KEY, DEFAULT_REMOTE_PROCESS_TTL_MS);

    myVerboseProcessing = false;

    myJvmArguments = new ArrayList<>();
    myArguments = new ArrayList<>();
    myEnv = new LinkedHashMap<>();
    myPassParentEnvs = true;
  }

  public ExternalSystemExecutionSettings(@NotNull ExternalSystemExecutionSettings settings) {
    myRemoteProcessIdleTtlInMs = settings.myRemoteProcessIdleTtlInMs;

    myVerboseProcessing = settings.myVerboseProcessing;

    myJvmArguments = new ArrayList<>(settings.myJvmArguments);
    myArguments = new ArrayList<>(settings.myArguments);
    myEnv = new LinkedHashMap<>(settings.myEnv);
    myPassParentEnvs = settings.myPassParentEnvs;

    settings.myUserData.copyUserDataTo(myUserData);
  }

  /**
   * @return ttl in milliseconds for the remote process (positive value); non-positive value if undefined
   */
  public long getRemoteProcessIdleTtlInMs() {
    return myRemoteProcessIdleTtlInMs;
  }

  public void setRemoteProcessIdleTtlInMs(long remoteProcessIdleTtlInMs) {
    myRemoteProcessIdleTtlInMs = remoteProcessIdleTtlInMs;
  }

  public boolean isVerboseProcessing() {
    return myVerboseProcessing;
  }

  public void setVerboseProcessing(boolean verboseProcessing) {
    myVerboseProcessing = verboseProcessing;
  }

  @NotNull
  public List<String> getJvmArguments() {
    return Collections.unmodifiableList(myJvmArguments);
  }

  @NotNull
  public List<String> getArguments() {
    return Collections.unmodifiableList(myArguments);
  }

  @NotNull
  public Map<String, String> getEnv() {
    return Collections.unmodifiableMap(myEnv);
  }

  public boolean isPassParentEnvs() {
    return myPassParentEnvs;
  }

  public boolean isDebugServerProcess() {
    var value = getUserData(DEBUG_SERVER_PROCESS_KEY);
    return ObjectUtils.chooseNotNull(value, false);
  }

  public ExternalSystemExecutionSettings withVmOptions(Collection<String> vmOptions) {
    myJvmArguments.addAll(vmOptions);
    return this;
  }

  public ExternalSystemExecutionSettings withVmOptions(String... vmOptions) {
    Collections.addAll(myJvmArguments, vmOptions);
    return this;
  }

  public ExternalSystemExecutionSettings withVmOption(String vmOption) {
    myJvmArguments.add(vmOption);
    return this;
  }

  public ExternalSystemExecutionSettings withArguments(Collection<String> arguments) {
    myArguments.addAll(arguments);
    return this;
  }

  public ExternalSystemExecutionSettings withArguments(String... arguments) {
    Collections.addAll(myArguments, arguments);
    return this;
  }

  public ExternalSystemExecutionSettings withArgument(String argument) {
    myArguments.add(argument);
    return this;
  }

  public void prependArguments(String... arguments) {
    myArguments.addAll(0, Arrays.asList(arguments));
  }

  public ExternalSystemExecutionSettings withEnvironmentVariables(Map<String, String> envs) {
    myEnv.putAll(envs);
    return this;
  }

  public ExternalSystemExecutionSettings passParentEnvs(boolean passParentEnvs) {
    myPassParentEnvs = passParentEnvs;
    return this;
  }

  @Nullable
  @Override
  public <U> U getUserData(@NotNull Key<U> key) {
    return myUserData.getUserData(key);
  }

  @Override
  public <U> void putUserData(@NotNull Key<U> key, U value) {
    myUserData.putUserData(key, value);
  }

  @Override
  public int hashCode() {
    int result = Long.hashCode(myRemoteProcessIdleTtlInMs);
    result = 31 * result + (myVerboseProcessing ? 1 : 0);
    result = 31 * result + myJvmArguments.hashCode();
    result = 31 * result + myArguments.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ExternalSystemExecutionSettings that = (ExternalSystemExecutionSettings)o;

    if (myRemoteProcessIdleTtlInMs != that.myRemoteProcessIdleTtlInMs) return false;
    if (myVerboseProcessing != that.myVerboseProcessing) return false;
    if (!myJvmArguments.equals(that.myJvmArguments)) return false;
    if (!myArguments.equals(that.myArguments)) return false;
    return true;
  }
}
