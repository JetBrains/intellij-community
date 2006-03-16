/*
 * Copyright 2000-2005 JetBrains s.r.o.
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



/**
 * A lock with the same semantics as builtin
 * Java synchronized locks: Once a thread has a lock, it
 * can re-obtain it any number of times without blocking.
 * The lock is made available to other threads when
 * as many releases as acquires have occurred.
 * <p>[<a href="http://gee.cs.oswego.edu/dl/classes/EDU/oswego/cs/dl/util/concurrent/intro.html"> Introduction to this package. </a>]
**/


public class ReentrantLock implements Sync  {

  protected Thread owner_ = null;
  protected long holds_ = 0;

  private Runnable myActionOnRelease = null;

  public void acquire() throws InterruptedException {
    //[Performace bottleneck?] if (Thread.interrupted()) throw new InterruptedException();
    Thread caller = Thread.currentThread();
    synchronized(this) {
      if (caller == owner_) 
        ++holds_;
      else {
        try {  
          while (owner_ != null) wait(); 
          owner_ = caller;
          holds_ = 1;
        }
        catch (InterruptedException ex) {
          notify();
          throw ex;
        }
      }
    }
  }  


  public boolean attempt(long msecs) throws InterruptedException {
    if (Thread.interrupted()) throw new InterruptedException();
    Thread caller = Thread.currentThread();
    synchronized(this) {
      if (caller == owner_) {
        ++holds_;
        return true;
      }
      else if (owner_ == null) {
        owner_ = caller;
        holds_ = 1;
        return true;
      }
      else if (msecs <= 0)
        return false;
      else {
        long waitTime = msecs;
        long start = System.currentTimeMillis();
        try {
          for (;;) {
            wait(waitTime); 
            if (caller == owner_) {
              ++holds_;
              return true;
            }
            else if (owner_ == null) {
              owner_ = caller;
              holds_ = 1;
              return true;
            }
            else {
              waitTime = msecs - (System.currentTimeMillis() - start);
              if (waitTime <= 0) 
                return false;
            }
          }
        }
        catch (InterruptedException ex) {
          notify();
          throw ex;
        }
      }
    }
  }  

  /**
   * Release the lock.
   * @exception Error thrown if not current owner of lock
   **/
  public synchronized void release()  {
    if (Thread.currentThread() != owner_)
      throw new Error("Illegal Lock usage");

    if (--holds_ == 0) {
      owner_ = null;
      notify();

      if (myActionOnRelease != null){
        Runnable action = myActionOnRelease;
        myActionOnRelease = null; // set null before run because it may throw an exception
        action.run();
      }
    }
  }

  /** 
   * Release the lock N times. <code>release(n)</code> is
   * equivalent in effect to:
   * <pre>
   *   for (int i = 0; i < n; ++i) release();
   * </pre>
   * <p>
   * @exception Error thrown if not current owner of lock
   * or has fewer than N holds on the lock
   **/
  public synchronized void release(long n) {
    if (Thread.currentThread() != owner_ || n > holds_)
      throw new Error("Illegal Lock usage"); 

    holds_ -= n;
    if (holds_ == 0) {
      owner_ = null;
      notify(); 

      if (myActionOnRelease != null){
        Runnable action = myActionOnRelease;
        myActionOnRelease = null; // set null before run because it may throw an exception
        action.run();
      }
    }
  }


  /**
   * Return the number of unreleased acquires performed
   * by the current thread.
   * Returns zero if current thread does not hold lock.
   **/
  public synchronized long holds() {
    if (Thread.currentThread() != owner_) return 0;
    return holds_;
  }

  public synchronized void performActionWhenReleased(Runnable action){
    if (holds() == 0){
      action.run();
    }
    else{
      myActionOnRelease = action;
    }
  }
}

