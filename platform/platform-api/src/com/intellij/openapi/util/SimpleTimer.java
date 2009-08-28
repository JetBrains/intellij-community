package com.intellij.openapi.util;

import java.util.*;

/**
 * Simple timer that keeps order of scheduled tasks
 */
public class SimpleTimer {

  private Timer ourTimer = new Timer(THREAD_NAME, true);

  private static SimpleTimer ourInstance = new SimpleTimer();
  private static final String THREAD_NAME = "SimpleTimer";

  private long myNextScheduledTime = Long.MAX_VALUE;
  private TimerTask myNextProcessingTask;

  private final Map<Long, ArrayList<SimpleTimerTask>> myTime2Task = new TreeMap<Long, ArrayList<SimpleTimerTask>>();

  public static SimpleTimer getInstance() {
    return ourInstance;
  }

  public SimpleTimerTask setUp(final Runnable runnable, long delay) {
    synchronized (myTime2Task) {
      final long current = System.currentTimeMillis();
      final long targetTime = current + delay;

      final SimpleTimerTask result = new SimpleTimerTask(targetTime, runnable);

      ArrayList<SimpleTimerTask> tasks = myTime2Task.get(targetTime);
      if (tasks == null) {
        tasks = new ArrayList<SimpleTimerTask>(2);
        myTime2Task.put(targetTime, tasks);
      }
      tasks.add(result);

      if (targetTime < myNextScheduledTime) {
        if (myNextProcessingTask != null) {
          myNextProcessingTask.cancel();
        }
        scheduleNext(delay, targetTime);
      }

      return result;
    }
  }

  private void scheduleNext(long delay, long targetTime) {
    myNextScheduledTime = targetTime;
    myNextProcessingTask = new TimerTask() {
      public void run() {
        processNext();
      }
    };
    ourTimer.schedule(myNextProcessingTask, delay);
  }

  private void processNext() {
    Ref<ArrayList<SimpleTimerTask>> tasks = new Ref<ArrayList<SimpleTimerTask>>();

    synchronized (myTime2Task) {
      final long current = System.currentTimeMillis();

      final Iterator<Long> times = myTime2Task.keySet().iterator();
      final Long time = times.next();
      tasks.set(myTime2Task.get(time));
      times.remove();

      if (!times.hasNext()) {
        myNextScheduledTime = Long.MAX_VALUE;
        myNextProcessingTask = null;
      } else {
        Long nextEffectiveTime = null;
        while (times.hasNext()) {
          Long nextTime = times.next();
          if (nextTime <= current) {
            tasks.get().addAll(myTime2Task.get(nextTime));
            times.remove();
          } else {
            nextEffectiveTime = nextTime;
            break;
          }
        }

        if (nextEffectiveTime == null) {
          myNextProcessingTask = null;
          myNextScheduledTime = Long.MAX_VALUE;
        } else {
          scheduleNext(nextEffectiveTime - current, nextEffectiveTime);
        }
      }
    }

    final ArrayList<SimpleTimerTask> toRun = tasks.get();
    for (SimpleTimerTask each : toRun) {
      if (!each.isCancelled()) {
        each.run();
      }
    }
  }

  public boolean isTimerThread() {
    return isTimerThread(Thread.currentThread());
  }

  public boolean isTimerThread(Thread thread) {
    return THREAD_NAME.equals(thread.getName());
  }

}