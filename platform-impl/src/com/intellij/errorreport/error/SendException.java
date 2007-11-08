package com.intellij.errorreport.error;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Aug 20, 2003
 * Time: 3:15:02 PM
 * To change this template use Options | File Templates.
 */
public class SendException extends RuntimeException {
  public SendException(Throwable cause) {
    super(cause);
  }
}
