/*
 * @author max
 */
package com.intellij.concurrency;

import com.intellij.openapi.components.ServiceManager;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public abstract class JobScheduler {
  public static JobScheduler getInstance() {
    return ServiceManager.getService(JobScheduler.class);
  }

  public abstract <T> Job<T> createJob(String title, int priority);


  public abstract ScheduledFuture<?> schedule(Runnable command, long delay,  TimeUnit unit);

  public abstract ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay,  long period, TimeUnit unit);

  public abstract ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay,  long delay, TimeUnit unit);
}