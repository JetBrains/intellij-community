package com.intellij.openapi.compiler;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 9, 2009
 */
public interface CompilerFilter {
  CompilerFilter ALL = new CompilerFilter() {
    public boolean acceptCompiler(Compiler compiler) {
      return true;
    }
  };
  /**
   * @param compiler
   * @return true if this compiler can be executed
   */
  boolean acceptCompiler(Compiler compiler);
}
