/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.compiler;



public interface PackagingCompiler extends FileProcessingCompiler{
  void processOutdatedItem(CompileContext context, String url, ValidityState state);
}
