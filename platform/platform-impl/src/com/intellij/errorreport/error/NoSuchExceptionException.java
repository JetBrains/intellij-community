package com.intellij.errorreport.error;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: May 19, 2003
 * Time: 10:46:39 PM
 * To change this template use Options | File Templates.
 */
public class NoSuchExceptionException extends Exception {
  private static final String NAME = "#com.intellij.errorreport.error.NoSuchExceptionException";

  public static boolean isException (Throwable e) {
    String str1 = e.getMessage();
    if (str1 == null)
      str1 = "";

    String str2 = NAME.substring(1);

    return str1.indexOf(str2) != -1;
  }

  public NoSuchExceptionException(String message) {
    super(message);
  }
}
