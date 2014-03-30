/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.refactoring.listeners.impl.impl;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider;
import com.intellij.refactoring.listeners.UndoRefactoringElementListener;
import com.intellij.refactoring.listeners.impl.RefactoringTransaction;
import com.intellij.util.containers.HashMap;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author dsl
 */
public class RefactoringTransactionImpl implements RefactoringTransaction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.listeners.impl.impl.RefactoringTransactionImpl");

  /**
   * Actions to be performed at commit.
   */
  private final ArrayList<Runnable> myRunnables = new ArrayList<Runnable>();
  private final List<RefactoringElementListenerProvider> myListenerProviders;
  private final Map<PsiElement,ArrayList<RefactoringElementListener>> myOldElementToListenerListMap = new HashMap<PsiElement,ArrayList<RefactoringElementListener>>();
  private final Map<PsiElement,RefactoringElementListener> myOldElementToTransactionListenerMap = new HashMap<PsiElement,RefactoringElementListener>();

  public RefactoringTransactionImpl(List<RefactoringElementListenerProvider> listenerProviders) {
    myListenerProviders = listenerProviders;
  }

  private void addAffectedElement(PsiElement oldElement) {
    if(myOldElementToListenerListMap.get(oldElement) != null) return;
    ArrayList<RefactoringElementListener> listenerList = new ArrayList<RefactoringElementListener>();
    for (RefactoringElementListenerProvider provider : myListenerProviders) {
      try {
        final RefactoringElementListener listener = provider.getListener(oldElement);
        if (listener != null) {
          listenerList.add(listener);
        }
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
    myOldElementToListenerListMap.put(oldElement, listenerList);
  }


  @Override
  public RefactoringElementListener getElementListener(PsiElement oldElement) {
    RefactoringElementListener listener =
      myOldElementToTransactionListenerMap.get(oldElement);
    if(listener == null) {
      listener = new MyRefactoringElementListener(oldElement);
      myOldElementToTransactionListenerMap.put(oldElement, listener);
    }
    return listener;
  }

  private class MyRefactoringElementListener implements RefactoringElementListener, UndoRefactoringElementListener {
    private final ArrayList<RefactoringElementListener> myListenerList;
    private MyRefactoringElementListener(PsiElement oldElement) {
      addAffectedElement(oldElement);
      myListenerList = myOldElementToListenerListMap.get(oldElement);
    }

    @Override
    public void elementMoved(@NotNull final PsiElement newElement) {
      myRunnables.add(new Runnable() {
        @Override
        public void run() {
          for (RefactoringElementListener refactoringElementListener : myListenerList) {
            try {
              refactoringElementListener.elementMoved(newElement);
            }
            catch (Throwable e) {
              LOG.error(e);
            }
          }
        }
      });
    }

    @Override
    public void elementRenamed(@NotNull final PsiElement newElement) {
      myRunnables.add(new Runnable() {
        @Override
        public void run() {
          for (RefactoringElementListener refactoringElementListener : myListenerList) {
            try {
              refactoringElementListener.elementRenamed(newElement);
            }
            catch (Throwable e) {
              LOG.error(e);
            }
          }
        }
      });
    }

    @Override
    public void undoElementMovedOrRenamed(@NotNull PsiElement newElement, @NotNull String oldQualifiedName) {
      for (RefactoringElementListener listener : myListenerList) {
        if (listener instanceof UndoRefactoringElementListener) {
          ((UndoRefactoringElementListener)listener).undoElementMovedOrRenamed(newElement, oldQualifiedName);
        }
      }
    }
  }

  @Override
  public void commit() {
    for (Runnable runnable : myRunnables) {
      runnable.run();
    }
  }

}
