
package com.intellij.util.concurrency;

import java.util.LinkedList;

public class WorkerThread extends Thread{
  private LinkedList myTasks = new LinkedList();
  private boolean myToDispose = false;
  private boolean myDisposed = false;

  public WorkerThread(String name) {
    super(name);
  }

  public boolean addTask(Runnable action) {
    synchronized(myTasks){
      if(myDisposed) return false;

      myTasks.add(action);
      myTasks.notifyAll();
      return true;
    }
  }

  public boolean addTaskFirst(Runnable action) {
    synchronized(myTasks){
      if(myDisposed) return false;

      myTasks.add(0, action);
      myTasks.notifyAll();
      return true;
    }
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

  public void run() {
    while(true){
      while(true){
        Runnable task;
        synchronized(myTasks){
          if (myTasks.isEmpty()) break;
          task = (Runnable)myTasks.removeFirst();
        }
        task.run();
      }

      synchronized(myTasks){
        if (myToDispose && myTasks.isEmpty()){
          myDisposed = true;
          return;
        }

        try{
          myTasks.wait();
        }
        catch(InterruptedException e){
        }
      }
    }
  }
}