/*
 * @author: Eugene Zhuravlev
 * Date: May 14, 2003
 * Time: 10:42:29 AM
 */
package com.intellij.compiler.make;



public interface ClassInfoProcessor {
  /**
   * @param classQName of a class info to be processed
   * @return true if superclasses of info should be processed and false otherwise
   */
  boolean process(int classQName) throws CacheCorruptedException;
}
