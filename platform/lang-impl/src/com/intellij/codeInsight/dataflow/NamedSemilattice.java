package com.intellij.codeInsight.dataflow;

/**
 * @author oleg
 */
public abstract class NamedSemilattice<T> implements Semilattice<T> {
  public boolean eq(final DFAMap<T> e1, final DFAMap<T> e2) {
    return e1.equals(e2);
  }
}