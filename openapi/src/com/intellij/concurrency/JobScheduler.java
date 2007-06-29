/*
 * @author max
 */
package com.intellij.concurrency;

import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NonNls;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

public abstract class JobScheduler {
  private static final ScheduledExecutorService ourScheduledExecutorService = Executors.newScheduledThreadPool(1, new ThreadFactory() {
    public Thread newThread(final Runnable r) {
      return new Thread(r, "Periodic tasks thread");
    }
  });

  public static JobScheduler getInstance() {
    return ServiceManager.getService(JobScheduler.class);
  }

  public abstract <T> Job<T> createJob(@NonNls String title, int priority);

  public static ScheduledExecutorService getScheduler() {
    return ourScheduledExecutorService;
  }
}