/*
 * @author max
 */
package com.intellij.psi.stubs;

public interface IndexSink {
  void occurence(StubIndexKey indexKey, Object value);
}