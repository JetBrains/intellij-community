package com.intellij.util.concurrency.readwrite;

import com.intellij.openapi.application.ApplicationListener;
import com.intellij.openapi.application.ApplicationManager;

public class WriteActionWaiter extends AbstractWaiter implements ApplicationListener {

  private Runnable myActionRunnable;

  public WriteActionWaiter(Runnable aActionRunnable) {
    myActionRunnable = aActionRunnable;

    setFinished(false);
    ApplicationManager.getApplication().addApplicationListener(this);
  }

  public void writeActionFinished(Object aRunnable) {
    if (aRunnable == myActionRunnable) {
      setFinished(true);

      ApplicationManager.getApplication().removeApplicationListener(this);
    }
  }

  public void applicationExiting() {
  }

  public void beforeWriteActionStart(Object action) {
  }

  public boolean canExitApplication() {
    return true;
  }

  public void writeActionStarted(Object action) {
  }
}
