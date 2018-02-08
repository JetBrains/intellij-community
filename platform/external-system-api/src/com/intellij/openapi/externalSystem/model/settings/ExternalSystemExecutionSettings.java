package com.intellij.openapi.externalSystem.model.settings;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
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

  private static final long serialVersionUID = 1L;

  private long myRemoteProcessIdleTtlInMs;
  private boolean myVerboseProcessing;
  @NotNull private final Set<String> myVmOptions;
  @NotNull private final List<String> myArguments;
  @NotNull
  private final Map<String, String> myEnv;
  private boolean myPassParentEnvs = true;

  @NotNull private final transient UserDataHolderBase myUserData = new UserDataHolderBase();

  public ExternalSystemExecutionSettings() {
    int ttl = SystemProperties.getIntProperty(REMOTE_PROCESS_IDLE_TTL_IN_MS_KEY, DEFAULT_REMOTE_PROCESS_TTL_MS);
    setRemoteProcessIdleTtlInMs(ttl);
    myVmOptions = new LinkedHashSet<>();
    myArguments = new ArrayList<>();
    myEnv = new LinkedHashMap<>();
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
  public Set<String> getVmOptions() {
    return Collections.unmodifiableSet(myVmOptions);
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

  public ExternalSystemExecutionSettings withVmOptions(Collection<String> vmOptions) {
    myVmOptions.addAll(vmOptions);
    return this;
  }

  public ExternalSystemExecutionSettings withVmOptions(String... vmOptions) {
    Collections.addAll(myVmOptions, vmOptions);
    return this;
  }

  public ExternalSystemExecutionSettings withVmOption(String vmOption) {
    myVmOptions.add(vmOption);
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
    int result = (int)(myRemoteProcessIdleTtlInMs ^ (myRemoteProcessIdleTtlInMs >>> 32));
    result = 31 * result + (myVerboseProcessing ? 1 : 0);
    result = 31 * result + myVmOptions.hashCode();
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
    if (!myVmOptions.equals(that.myVmOptions)) return false;
    if (!myArguments.equals(that.myArguments)) return false;
    return true;
  }
}
