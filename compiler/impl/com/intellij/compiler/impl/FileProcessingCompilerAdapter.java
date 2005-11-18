package com.intellij.compiler.impl;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.FileProcessingCompiler;
import com.intellij.openapi.compiler.ValidityState;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 *         Date: Oct 16
 * @author 2003
 */
public class FileProcessingCompilerAdapter {
  private final CompileContext myCompileContext;
  private final FileProcessingCompiler myCompiler;

  protected FileProcessingCompilerAdapter(CompileContext compileContext, FileProcessingCompiler compiler) {
    myCompileContext = compileContext;
    myCompiler = compiler;
  }

  public CompileContext getCompileContext() {
    return myCompileContext;
  }

  public FileProcessingCompiler.ProcessingItem[] getProcessingItems() {
    return myCompiler.getProcessingItems(getCompileContext());
  }

  public FileProcessingCompiler.ProcessingItem[] process(FileProcessingCompiler.ProcessingItem[] items) {
    return myCompiler.process(getCompileContext(), items);
  }

  public final FileProcessingCompiler getCompiler() {
    return myCompiler;
  }

  public void processOutdatedItem(CompileContext context, String url, @Nullable ValidityState state){}
}
