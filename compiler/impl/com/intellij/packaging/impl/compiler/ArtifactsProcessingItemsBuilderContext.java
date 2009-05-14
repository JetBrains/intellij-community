package com.intellij.packaging.impl.compiler;

import com.intellij.compiler.impl.packagingCompiler.ProcessingItemsBuilderContext;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.packaging.elements.ArtifactIncrementalCompilerContext;

/**
 * @author nik
 */
public class ArtifactsProcessingItemsBuilderContext extends ProcessingItemsBuilderContext implements ArtifactIncrementalCompilerContext {
  public ArtifactsProcessingItemsBuilderContext(CompileContext compileContext) {
    super(compileContext);
  }
}
