/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.openapi.fileTypes.FileTypeExtension;

public class BinaryFileStubBuilders extends FileTypeExtension<BinaryFileStubBuilder>{
  public static final BinaryFileStubBuilders INSTANCE = new BinaryFileStubBuilders();
  public BinaryFileStubBuilders() {
    super("com.intellij.filetype.stubBuilder");
  }
}
