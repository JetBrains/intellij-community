/*
 * @author: Eugene Zhuravlev
 * Date: Apr 1, 2003
 * Time: 5:58:41 PM
 */
package com.intellij.compiler.impl.javaCompiler;

import com.intellij.openapi.compiler.TranslatingCompiler;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

public class OutputItemImpl implements TranslatingCompiler.OutputItem{

  private final String myOutputPath;
  private final String myOutputDir;
  private final VirtualFile mySourceFile;

  public OutputItemImpl(VirtualFile packageInfoFile) {
    this(null, null, packageInfoFile);
  }

  /**
   * @param outputDir
   * @param outputPath relative to output directory path of the output file ('/' slashes used)
   * @param sourceFile corresponding source file
   */
  public OutputItemImpl(String outputDir, @Nullable String outputPath, VirtualFile sourceFile) {
    myOutputDir = outputDir;
    myOutputPath = outputPath;
    mySourceFile = sourceFile;
  }

  public String getOutputPath() {
    return (myOutputPath != null) ? myOutputDir + "/" + myOutputPath : null;
  }

  public String getOutputRootDirectory() {
    return myOutputDir;
  }

  public VirtualFile getSourceFile() {
    return mySourceFile;
  }
}
