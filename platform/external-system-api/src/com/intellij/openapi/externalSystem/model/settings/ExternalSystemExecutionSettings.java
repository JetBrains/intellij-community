package com.intellij.openapi.externalSystem.model.settings;

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * There is a possible case that all work with external system is performed at a separate slave process (e.g. when we use
 * an external system api and don't want to pollute ide process with it). The code which is executed at the external process
 * is unaware of any ide-local settings defined by a user then.
 * <p/>
 * Example: a user experiences problem with external system integration and we instruct him to define a dedicated system property
 * which triggers verbose processing. That property is not visible to a slave process then, so, we need to deliver the data
 * to it somehow. That's why this class has been introduced - it's just a holder for such data.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/9/11 12:12 PM
 */
public class ExternalSystemExecutionSettings implements Serializable {

  public static final String REMOTE_PROCESS_IDLE_TTL_IN_MS_KEY = "external.system.remote.process.idle.ttl.ms";
  private static final int    DEFAULT_REMOTE_PROCESS_TTL_MS     = 60000;

  private static final long serialVersionUID = 1L;

  @NotNull private final AtomicLong    myRemoteProcessIdleTtlInMs = new AtomicLong();
  @NotNull private final AtomicBoolean myVerboseProcessing        = new AtomicBoolean();

  @NotNull private final AtomicReference<ExternalSystemTaskNotificationListener> myNotificationListener =
    new AtomicReference<>();

  public ExternalSystemExecutionSettings() {
    int ttl = SystemProperties.getIntProperty(REMOTE_PROCESS_IDLE_TTL_IN_MS_KEY, DEFAULT_REMOTE_PROCESS_TTL_MS);
    setRemoteProcessIdleTtlInMs(ttl);
  }

  /**
   * @return ttl in milliseconds for the remote process (positive value); non-positive value if undefined
   */
  public long getRemoteProcessIdleTtlInMs() {
    return myRemoteProcessIdleTtlInMs.get();
  }

  public void setRemoteProcessIdleTtlInMs(long remoteProcessIdleTtlInMs) {
    myRemoteProcessIdleTtlInMs.set(remoteProcessIdleTtlInMs);
  }

  public boolean isVerboseProcessing() {
    return myVerboseProcessing.get();
  }

  public void setVerboseProcessing(boolean verboseProcessing) {
    myVerboseProcessing.set(verboseProcessing);
  }

  @Override
  public int hashCode() {
    int result = (int)(myRemoteProcessIdleTtlInMs.get() ^ (myRemoteProcessIdleTtlInMs.get() >>> 32));
    result = 31 * result + (myVerboseProcessing.get() ? 1 : 0);
    ExternalSystemTaskNotificationListener listener = myNotificationListener.get();
    return listener == null ? result : 31 * result + listener.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ExternalSystemExecutionSettings that = (ExternalSystemExecutionSettings)o;

    if (myRemoteProcessIdleTtlInMs.get() != that.myRemoteProcessIdleTtlInMs.get()) return false;
    if (myVerboseProcessing.get() != that.myVerboseProcessing.get()) return false;
    ExternalSystemTaskNotificationListener notificationListener = myNotificationListener.get();
    ExternalSystemTaskNotificationListener thatNotificationListener = that.myNotificationListener.get();
    if ((notificationListener == null && thatNotificationListener != null)
        || (notificationListener != null && !notificationListener.equals(thatNotificationListener)))
    {
      return false;
    }

    return true;
  }
}
