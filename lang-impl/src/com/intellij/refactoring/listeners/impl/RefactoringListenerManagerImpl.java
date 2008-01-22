package com.intellij.refactoring.listeners.impl;

import com.intellij.refactoring.listeners.RefactoringElementListenerProvider;
import com.intellij.refactoring.listeners.RefactoringListenerManager;
import com.intellij.refactoring.listeners.impl.impl.RefactoringTransactionImpl;

import java.util.ArrayList;

/**
 * @author dsl
 */
public class RefactoringListenerManagerImpl extends RefactoringListenerManager {
  private final ArrayList<RefactoringElementListenerProvider> myListenerProviders;

  public RefactoringListenerManagerImpl() {
    myListenerProviders = new ArrayList<RefactoringElementListenerProvider>();
  }

  public void addListenerProvider(RefactoringElementListenerProvider provider) {
    myListenerProviders.add(provider);
  }

  public void removeListenerProvider(RefactoringElementListenerProvider provider) {
    myListenerProviders.remove(provider);
  }

  public RefactoringTransaction startTransaction() {
    return new RefactoringTransactionImpl(myListenerProviders);
  }
}
