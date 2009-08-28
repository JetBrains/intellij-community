package com.intellij.psi.impl.cache.impl.id;

import com.intellij.openapi.fileTypes.FileTypeExtension;

/**
 * @author yole
 */
public class IdIndexers extends FileTypeExtension<FileTypeIdIndexer> {
  public static IdIndexers INSTANCE = new IdIndexers();

  private IdIndexers() {
    super("com.intellij.idIndexer");
  }
}
