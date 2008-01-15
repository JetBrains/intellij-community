package com.intellij.openapi.roots.impl;

import com.intellij.openapi.roots.ContentEntry;

/**
 *  @author dsl
 */
public interface ClonableContentEntry {
  ContentEntry cloneEntry(RootModelImpl rootModel);
}
