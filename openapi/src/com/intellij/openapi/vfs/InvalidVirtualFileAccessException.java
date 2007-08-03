/*
 * @author max
 */
package com.intellij.openapi.vfs;

public class InvalidVirtualFileAccessException extends RuntimeException {
  public InvalidVirtualFileAccessException(final VirtualFile file) {
    super("Accessing invalid virtual file: " + file.getUrl());
  }
}