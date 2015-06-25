package org.jetbrains.platform.loader;

/**
 * @author nik
 */
public class PlatformLoaderException extends RuntimeException {
  public PlatformLoaderException(String message) {
    super(message);
  }

  public PlatformLoaderException(String message, Throwable cause) {
    super(message, cause);
  }
}
