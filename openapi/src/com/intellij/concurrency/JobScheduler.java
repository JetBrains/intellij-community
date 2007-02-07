/*
 * @author max
 */
package com.intellij.concurrency;

import com.intellij.openapi.components.ServiceManager;

public abstract class JobScheduler {
  public static JobScheduler getInstance() {
    return ServiceManager.getService(JobScheduler.class);
  }

  public abstract <T> Job<T> createJob(String titile, int priority);
}