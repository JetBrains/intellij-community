/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.project;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class DumbService {
  public static final boolean UPDATE_IN_BACKGROUND = !ApplicationManager.getApplication().isUnitTestMode() &&
                                                     "true".equals(System.getProperty("update.indices.in.background"));

  public static final Topic<DumbModeListener> DUMB_MODE = new Topic<DumbModeListener>("dumb mode", DumbModeListener.class);

  public abstract boolean isDumb();

  public abstract void runWhenSmart(Runnable runnable);

  public void waitForSmartMode() {
    final Application application = ApplicationManager.getApplication();
    if (!application.isUnitTestMode()) {
      assert !application.isDispatchThread();
      assert !application.isReadAccessAllowed();
    }

    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    runWhenSmart(new Runnable() {
          public void run() {
            semaphore.up();
          }
        });
    semaphore.waitFor();
  }

  public void smartInvokeLater(@NotNull final Runnable runnable) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        runWhenSmart(runnable);
      }
    });
  }

  public static DumbService getInstance() {
    return ServiceManager.getService(DumbService.class);
  }


  public interface DumbModeListener {

    /**
     * The events arrive on EDT before write action
     */
    void beforeEnteringDumbMode();

    /**
     * The event arrives on EDT inside write action
     */
    void enteredDumbMode();

    /**
     * The event arrives on EDT inside write action
     */
    void exitDumbMode();

  }
}
