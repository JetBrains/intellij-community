package com.intellij.psi.impl.cache;


/**
 * @author max
 */
public interface RepositoryItemView {
  String getName(long id);

  long getParent(long id);

  long[] getChildren(long id, RepositoryElementType type);

  long getContainingFile(long id);

  boolean isCompiled(long id);
}