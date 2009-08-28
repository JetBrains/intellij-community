/*
 * @author max
 */
package com.intellij.openapi.vfs;

/**
 * A marker interface for {@link VirtualFile#getOutputStream(Object)} to take extra caution overwriting existing content.
 * Specifically, if the operation fails for certain reason (like not enough disk space left) prior content shall not be overwritten (partially).
 */
public interface SafeWriteRequestor {
}
