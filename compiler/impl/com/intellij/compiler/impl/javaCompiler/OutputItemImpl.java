/*
 * @author: Eugene Zhuravlev
 * Date: Apr 1, 2003
 * Time: 5:58:41 PM
 */
package com.intellij.compiler.impl.javaCompiler;

import com.intellij.openapi.compiler.TranslatingCompiler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ByteTrie;
import org.jetbrains.annotations.Nullable;

public class OutputItemImpl implements TranslatingCompiler.OutputItem{

  private final int myOutputPath;
  private final String myOutputDir;
  private final VirtualFile mySourceFile;
  private final ByteTrie myTrie;

  public OutputItemImpl(VirtualFile packageInfoFile) {
    this(null, null, null, packageInfoFile);
  }

  /**
   * @param trie
   * @param outputDir
   * @param outputPath relative to output directory path of the output file ('/' slashes used)
   * @param sourceFile corresponding source file
   */
  public OutputItemImpl(ByteTrie trie, String outputDir, @Nullable String outputPath, VirtualFile sourceFile) {
    myTrie = trie;
    myOutputDir = outputDir;
    if (trie != null) {
      myOutputPath = outputPath != null? trie.getHashCode(outputPath) : -1;
    }
    else {
      myOutputPath = -1;
    }
    mySourceFile = sourceFile;
  }

  public String getOutputPath() {
    return (myOutputPath >= 0) ? myOutputDir + "/" + myTrie.getString(myOutputPath) : null;
  }

  public String getOutputRootDirectory() {
    return myOutputDir;
  }

  public VirtualFile getSourceFile() {
    return mySourceFile;
  }
}
