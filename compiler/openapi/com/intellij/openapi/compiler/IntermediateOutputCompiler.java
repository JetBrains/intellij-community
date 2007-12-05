package com.intellij.openapi.compiler;

/**
 * A tag interface denoting that compiler's output should go into 'intermediate' directory 
 * and can be used as input to another compiler 
 */
public interface IntermediateOutputCompiler extends Compiler{
}
