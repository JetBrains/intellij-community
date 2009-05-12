/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.project;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class DumbService {
  public static final Topic<DumbModeListener> DUMB_MODE = new Topic<DumbModeListener>("dumb mode", DumbModeListener.class);

  /**
   * @return whether IntelliJ IDEA is in dumb mode, which means that right now indices are updated in background.
   * IDEA offers only limited functionality at such times, e.g. plain text file editing and version control operations.
   */
  public abstract boolean isDumb();

  /**
   * Run the runnable when dumb mode ends
   * @param runnable runnable to run
   */
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

  /**
   * Invoke the runnable later on EventDispatchThread AND when IDEA isn't in dumb mode
   * @param runnable runnable
   */
  public void smartInvokeLater(@NotNull final Runnable runnable) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        runWhenSmart(runnable);
      }
    });
  }

  public void smartInvokeLater(@NotNull final Runnable runnable, ModalityState modalityState) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        runWhenSmart(runnable);
      }
    }, modalityState);
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
