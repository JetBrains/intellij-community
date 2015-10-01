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
package com.intellij.util.concurrency;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.TimeoutUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;


/**
 * @deprecated use lightweight com.intellij.util.concurrency.QueueProcessor instead
 */
public class WorkerThread implements Runnable, Disposable {
  private final LinkedList<Runnable> myTasks = new LinkedList<Runnable>();
  private boolean myToDispose = false;
  private boolean myDisposed = false;
  private final int mySleep;
  private final String myName;

  public WorkerThread(@NonNls String name, int sleep) {
    mySleep = sleep;
    myName = name;
  }

  public void start() {
    ApplicationManager.getApplication().executeOnPooledThread(this);
  }
  
  public boolean addTask(Runnable action) {
    synchronized(myTasks){
      if(myDisposed) return false;

      myTasks.add(action);
      myTasks.notifyAll();
      return true;
    }
  }

  public boolean addTaskFirst(@NotNull Runnable action) {
    synchronized(myTasks){
      if(myDisposed) return false;

      myTasks.add(0, action);
      myTasks.notifyAll();
      return true;
    }
  }

  @Override
  public void dispose() {
    dispose(true);
  }

  public void dispose(boolean cancelTasks){
    synchronized(myTasks){
      if (cancelTasks){
        myTasks.clear();
      }
      myToDispose = true;
      myTasks.notifyAll();
    }
  }

  public void cancelTasks() {
    synchronized(myTasks){
      myTasks.clear();
      myTasks.notifyAll();
    }
  }

  public boolean isDisposeRequested() {
    synchronized(myTasks){
      return myToDispose;
    }
  }

  public boolean isDisposed() {
    synchronized(myTasks){
      return myDisposed;
    }
  }

  @Override
  public void run() {
    while (true) {
      while (true) {
        Runnable task;
        synchronized (myTasks) {
          if (myTasks.isEmpty()) break;
          task = myTasks.removeFirst();
        }
        task.run();
        if (mySleep > 0) {
          TimeoutUtil.sleep(mySleep);
        }
      }

      synchronized (myTasks) {
        if (myToDispose && myTasks.isEmpty()) {
          myDisposed = true;
          return;
        }

        try {
          myTasks.wait();
        }
        catch (InterruptedException ignored) {
        }
      }
    }
  }
}
