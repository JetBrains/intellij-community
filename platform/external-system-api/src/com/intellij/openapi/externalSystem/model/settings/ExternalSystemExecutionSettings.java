package com.intellij.openapi.externalSystem.model.settings;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;

/**
 * Holds execution settings of particular invocation of an external system.
 * E.g. task running or project importing.
 */
public class ExternalSystemExecutionSettings implements Serializable, UserDataHolder {

  public static final String REMOTE_PROCESS_IDLE_TTL_IN_MS_KEY = "external.system.remote.process.idle.ttl.ms";
  private static final int DEFAULT_REMOTE_PROCESS_TTL_MS = 60000;

  private static final long serialVersionUID = 1L;

  private long myRemoteProcessIdleTtlInMs;
  private boolean myVerboseProcessing;

  @NotNull private transient UserDataHolderBase myUserData = new UserDataHolderBase();

  public ExternalSystemExecutionSettings() {
    int ttl = SystemProperties.getIntProperty(REMOTE_PROCESS_IDLE_TTL_IN_MS_KEY, DEFAULT_REMOTE_PROCESS_TTL_MS);
    setRemoteProcessIdleTtlInMs(ttl);
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
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ExternalSystemExecutionSettings that = (ExternalSystemExecutionSettings)o;

    if (myRemoteProcessIdleTtlInMs != that.myRemoteProcessIdleTtlInMs) return false;
    if (myVerboseProcessing != that.myVerboseProcessing) return false;
    return true;
  }
}
