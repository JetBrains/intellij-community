/*
 * @author: Eugene Zhuravlev
 * Date: Jul 10, 2003
 * Time: 4:51:25 PM
 */
package com.intellij.compiler.make;

import com.intellij.openapi.compiler.CompilerBundle;

public class CacheCorruptedException extends Exception{
  private static final String DEFAULT_MESSAGE = CompilerBundle.message("error.dependency.info.on.disk.corrupted");
  public CacheCorruptedException(String message) {
    super((message == null || message.length() == 0)? DEFAULT_MESSAGE : message);
  }

  public CacheCorruptedException(Throwable cause) {
    super(DEFAULT_MESSAGE, cause);
  }

  public CacheCorruptedException(String message, Throwable cause) {
    super((message == null || message.length() == 0)? DEFAULT_MESSAGE : message, cause);
  }

  public String getMessage() {
    return super.getMessage();
  }
}
