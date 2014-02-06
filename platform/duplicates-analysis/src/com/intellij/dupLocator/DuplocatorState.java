package com.intellij.dupLocator;

/**
 * @author Eugene.Kudelevsky
 */
public interface DuplocatorState {
  
  int getLowerBound();
  
  int getDiscardCost();

}
