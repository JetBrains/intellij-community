/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.compiler;

/**
 * Allows one to perform any validation on the files in given compile scope
 * Error/Warning messages should be added to the CompileContext object  
 */
public interface Validator extends FileProcessingCompiler {
}
