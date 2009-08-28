/*
 * @author max
 */
package com.intellij.openapi.fileTypes;

public class BinaryFileTypeDecompilers extends FileTypeExtension<BinaryFileDecompiler>{
  public static final BinaryFileTypeDecompilers INSTANCE = new BinaryFileTypeDecompilers();

  private BinaryFileTypeDecompilers() {
    super("com.intellij.filetype.decompiler");
  }
}