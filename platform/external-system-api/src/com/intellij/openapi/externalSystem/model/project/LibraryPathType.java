package com.intellij.openapi.externalSystem.model.project;

import com.intellij.openapi.roots.OrderRootType;

/**
 * Note that current enum duplicates {@link OrderRootType}. We can't use the later directly because it's not properly setup
 * for serialization/deserialization.
 * 
 * @author Denis Zhdanov
 * @since 8/10/11 6:37 PM
 */
public enum LibraryPathType {
  BINARY, SOURCE, DOC
}
