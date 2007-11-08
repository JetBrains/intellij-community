package com.intellij.ui;

import org.jetbrains.annotations.NonNls;


/**
 * Demos that animate extend this class.
 */
public abstract class AnimatingSurface extends Surface implements Runnable {
  public Thread thread;
  @NonNls protected static final String DEFAULT_THREAD_NAME = "Animating surface";
  @NonNls protected static final String THREAD_NAME_SUFFIX = " Demo";

  public abstract void step(int w, int h);

  public abstract void reset(int newwidth, int newheight);

  public void start() {
    if (thread == null && !dontThread){
      thread = new Thread(this, DEFAULT_THREAD_NAME);
      thread.setPriority(Thread.MIN_PRIORITY);
      thread.setName(name + THREAD_NAME_SUFFIX);
      thread.start();
    }
  }

  public synchronized void stop() {
    if (thread != null){
      thread.interrupt();
    }
    thread = null;
    notifyAll();
  }

  public void run() {

    Thread me = Thread.currentThread();

    while(thread == me && !isShowing() || getSize().width == 0){
      try{
        Thread.sleep(300);
      }
      catch(InterruptedException e){
      }
    }

    while(thread == me){
      repaint();
      try{
        Thread.sleep(sleepAmount);
      }
      catch(InterruptedException e){
      }
    }
    thread = null;
  }
}
