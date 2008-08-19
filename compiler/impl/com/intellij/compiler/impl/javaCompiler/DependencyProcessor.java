package com.intellij.compiler.impl.javaCompiler;

import com.intellij.openapi.compiler.CompileContext;

/**
 * @author Eugene Zhuravlev
 *         Date: Aug 19, 2008
 */
public interface DependencyProcessor {
  void processDependencies(CompileContext context, int classQualifiedName);
}
