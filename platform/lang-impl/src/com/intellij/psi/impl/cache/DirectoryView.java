package com.intellij.psi.impl.cache;

/**
 * @author max
 */ 
public interface DirectoryView extends RepositoryItemView {
  long[] getDirs(long id);
  long[] getFiles(long id);
}
