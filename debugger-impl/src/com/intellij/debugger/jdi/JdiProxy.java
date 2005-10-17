package com.intellij.debugger.jdi;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Jul 10, 2003
 * Time: 4:53:44 PM
 * To change this template use Options | File Templates.
 */
public abstract class JdiProxy {
  protected JdiTimer myTimer;
  private int myTimeStamp = 0;

  public JdiProxy(JdiTimer timer) {
    myTimer = timer;
    myTimeStamp = myTimer.getCurrentTime();
  }

  protected void checkValid() {
    if(!isValid()) {
      myTimeStamp = myTimer.getCurrentTime();
      clearCaches();
    }
  }

  /**
   * @deprecated for testing only
   * @return
   */
  public boolean isValid() {
    return myTimeStamp == myTimer.getCurrentTime();
  }

  protected abstract void clearCaches();
}
