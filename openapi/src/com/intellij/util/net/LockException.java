package com.intellij.util.net;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Dec 16, 2003
 * Time: 9:47:01 PM
 * To change this template use Options | File Templates.
 */
public class LockException extends Exception {
  public LockException(int port) {
    super (Integer.toString(port));
  }
}
