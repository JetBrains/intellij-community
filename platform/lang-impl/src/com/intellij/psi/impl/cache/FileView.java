package com.intellij.psi.impl.cache;

/**
 * @author max
 */
public interface FileView extends RepositoryItemView {
  long getTimestamp(long fileId);

  String getPackageName(long fileId);

  int getImportStatementsCount(long fileId);

  boolean isImportOnDemand(long fileId, int importStatementIdx);

  boolean isImportStatic(long fileId, int importStatementIdx);

  String getImportQualifiedName(long fileId, int importStatementIdx);

  long[] getClasses(long fileId);

  long[] getAllClasses(long fileId);

  String getSourceFileName(long classId);

}
