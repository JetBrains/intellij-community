/*
 * @author max
 */
package com.intellij.psi.stubs;

public interface IndexSink {
  void occurence(String indexId, Object value);
}