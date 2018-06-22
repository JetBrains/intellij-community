// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.rt.debugger.agent;

import sun.misc.JavaLangAccess;
import sun.misc.SharedSecrets;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author egor
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class CaptureStorage {
  private static final ReferenceQueue KEY_REFERENCE_QUEUE = new ReferenceQueue();
  private static final ConcurrentMap<WeakReference, CapturedStack> STORAGE = new ConcurrentHashMap<WeakReference, CapturedStack>();

  @SuppressWarnings("SSBasedInspection")
  private static final ThreadLocal<Deque<InsertMatch>> CURRENT_STACKS = new ThreadLocal<Deque<InsertMatch>>() {
    @Override
    protected Deque<InsertMatch> initialValue() {
      return new LinkedList<InsertMatch>();
    }
  };

  private static boolean DEBUG = false;
  private static boolean ENABLED = true;

  //// METHODS CALLED FROM THE USER PROCESS

  @SuppressWarnings("unused")
  public static void capture(Object key) {
    if (!ENABLED) {
      return;
    }
    try {
      Throwable exception = new Throwable();
      if (DEBUG) {
        System.out.println("capture " + getCallerDescriptor(exception) + " - " + key);
      }
      CapturedStack stack = createCapturedStack(exception, CURRENT_STACKS.get().peekLast());
      processQueue();
      WeakKey keyRef = new WeakKey(key, stack, KEY_REFERENCE_QUEUE);
      STORAGE.put(keyRef, stack);
    }
    catch (Exception e) {
      handleException(e);
    }
  }

  @SuppressWarnings("unused")
  public static void insertEnter(Object key) {
    if (!ENABLED) {
      return;
    }
    try {
      //noinspection SuspiciousMethodCalls
      CapturedStack stack = STORAGE.get(new HardKey(key));
      Deque<InsertMatch> currentStacks = CURRENT_STACKS.get();
      if (stack != null) {
        Throwable exception = new Throwable();
        currentStacks.add(InsertMatch.create(stack, exception));
        if (DEBUG) {
          System.out.println("insert " + getCallerDescriptor(exception) + " -> " + key + ", stack saved (" + currentStacks.size() + ")");
        }
      }
      else {
        currentStacks.add(InsertMatch.EMPTY);
        if (DEBUG) {
          System.out.println(
            "insert " + getCallerDescriptor(new Throwable()) + " -> " + key + ", no stack found (" + currentStacks.size() + ")");
        }
      }
    }
    catch (Exception e) {
      handleException(e);
    }
  }

  @SuppressWarnings("unused")
  public static void insertExit(Object key) {
    if (!ENABLED) {
      return;
    }
    try {
      Deque<InsertMatch> currentStacks = CURRENT_STACKS.get();
      currentStacks.removeLast();
      if (DEBUG) {
        System.out.println(
          "insert " + getCallerDescriptor(new Throwable()) + " <- " + key + ", stack removed (" + currentStacks.size() + ")");
      }
    }
    catch (Exception e) {
      handleException(e);
    }
  }

  //// END - METHODS CALLED FROM THE USER PROCESS

  private static void processQueue() {
    WeakKey key;
    while ((key = (WeakKey)KEY_REFERENCE_QUEUE.poll()) != null) {
      STORAGE.remove(key, key.myValue);
    }
  }

  // only for map queries
  private static class HardKey {
    private final Object myKey;
    private final int myHash;

    public HardKey(Object key) {
      myKey = key;
      myHash = System.identityHashCode(key);
    }

    @Override
    public boolean equals(Object o) {
      return this == o || (o instanceof WeakKey && ((WeakKey)o).get() == myKey);
    }

    public int hashCode() {
      return myHash;
    }
  }

  private static class WeakKey extends WeakReference {
    private final int myHash;
    private final CapturedStack myValue;

    public WeakKey(Object key, CapturedStack value, ReferenceQueue q) {
      //noinspection unchecked
      super(key, q);
      myHash = System.identityHashCode(key);
      myValue = value;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof WeakKey)) return false;
      Object t = get();
      Object u = ((WeakKey)o).get();
      if (t == null || u == null) return false;
      return t == u;
    }

    @Override
    public int hashCode() {
      return myHash;
    }
  }

  private static CapturedStack createCapturedStack(Throwable exception, InsertMatch insertMatch) {
    if (insertMatch != null && insertMatch != InsertMatch.EMPTY) {
      CapturedStack stack = new DeepCapturedStack(exception, insertMatch);
      if (stack.getRecursionDepth() > 100) {
        ArrayList<StackTraceElement> trace = getStackTrace(stack, 500);
        trace.trimToSize();
        stack = new UnwindCapturedStack(trace);
      }
      return stack;
    }
    return new ExceptionCapturedStack(exception);
  }

  private interface CapturedStack {
    List<StackTraceElement> getStackTrace();
    int getRecursionDepth();
  }

  private static class UnwindCapturedStack implements CapturedStack {
    final List<StackTraceElement> myStackTraceElements;

    public UnwindCapturedStack(List<StackTraceElement> elements) {
      myStackTraceElements = elements;
    }

    @Override
    public List<StackTraceElement> getStackTrace() {
      return myStackTraceElements;
    }

    @Override
    public int getRecursionDepth() {
      return 0;
    }
  }

  private static class ExceptionCapturedStack implements CapturedStack {
    final Throwable myException;

    private ExceptionCapturedStack(Throwable exception) {
      myException = exception;
    }

    @Override
    public List<StackTraceElement> getStackTrace() {
      StackTraceElement[] stackTrace = myException.getStackTrace();
      return Arrays.asList(stackTrace).subList(1, stackTrace.length);
    }

    @Override
    public int getRecursionDepth() {
      return 0;
    }
  }

  private static class DeepCapturedStack extends ExceptionCapturedStack {
    final InsertMatch myInsertMatch;
    final int myRecursionDepth;

    public DeepCapturedStack(Throwable exception, InsertMatch insertMatch) {
      super(exception);
      myInsertMatch = insertMatch;
      myRecursionDepth = insertMatch.myStack.getRecursionDepth() + 1;
    }

    @Override
    public int getRecursionDepth() {
      return myRecursionDepth;
    }
  }

  private static abstract class InsertMatch {
    private final CapturedStack myStack;

    static final InsertMatch EMPTY = new InsertMatch(null) {
      @Override
      int getDepth() {
        return 0;
      }
    };

    private InsertMatch(CapturedStack stack) {
      myStack = stack;
    }

    static InsertMatch create(CapturedStack stack, Throwable throwable) {
      return ourJavaLangAccess != null
             ? new WithDepth(stack, ourJavaLangAccess.getStackTraceDepth(throwable))
             : new WithThrowable(stack, throwable);
    }

    abstract int getDepth();

    static class WithDepth extends InsertMatch {
      final int myDepth;

      public WithDepth(CapturedStack stack, int depth) {
        super(stack);
        myDepth = depth;
      }

      @Override
      public int getDepth() {
        return myDepth;
      }
    }

    static class WithThrowable extends InsertMatch {
      final Throwable myThrowable;

      public WithThrowable(CapturedStack stack, Throwable throwable) {
        super(stack);
        this.myThrowable = throwable;
      }

      @Override
      int getDepth() {
        return myThrowable.getStackTrace().length;
      }
    }
  }

  // to be run from the debugger
  @SuppressWarnings("unused")
  public static Object[][] getRelatedStack(Object key, int limit) {
    //noinspection SuspiciousMethodCalls
    CapturedStack stack = STORAGE.get(new HardKey(key));
    if (stack == null) {
      return null;
    }
    List<StackTraceElement> stackTrace = getStackTrace(stack, limit);
    Object[][] res = new Object[stackTrace.size()][];
    for (int i = 0; i < stackTrace.size(); i++) {
      StackTraceElement elem = stackTrace.get(i);
      if (elem == null) {
        res[i] = null;
      }
      else {
        res[i] = new Object[]{elem.getClassName(), elem.getFileName(), elem.getMethodName(), String.valueOf(elem.getLineNumber())};
      }
    }
    return res;
  }

  private static ArrayList<StackTraceElement> getStackTrace(CapturedStack stack, int limit) {
    ArrayList<StackTraceElement> res = new ArrayList<StackTraceElement>();
    while (stack != null && res.size() <= limit) {
      List<StackTraceElement> stackTrace = stack.getStackTrace();
      if (stack instanceof DeepCapturedStack) {
        InsertMatch match = ((DeepCapturedStack)stack).myInsertMatch;
        if (match != null && match != InsertMatch.EMPTY) {
          stackTrace = stackTrace.subList(0, stackTrace.size() - match.getDepth() + 2);
          stack = match.myStack;
        }
      }
      else {
        stack = null;
      }
      res.addAll(stackTrace);
      if (stack != null) {
        res.add(null);
      }
    }
    return res;
  }

  public static void setDebug(boolean debug) {
    DEBUG = debug;
  }

  public static void setEnabled(boolean enabled) {
    ENABLED = enabled;
  }

  private static final JavaLangAccess ourJavaLangAccess;
  static {
    JavaLangAccess access;
    try {
      access = SharedSecrets.getJavaLangAccess();
      if (access != null) {
        access.getStackTraceDepth(new Throwable()); // may throw UnsupportedOperationException in some implementations
      }
    }
    catch (Throwable e) {
      access = null;
    }
    ourJavaLangAccess = access;
  }

  private static void handleException(Throwable e) {
    ENABLED = false;
    System.err.println("Critical error in IDEA Async Stacktraces instrumenting agent. Agent is now disabled. Please report to IDEA support:");
    //noinspection CallToPrintStackTrace
    e.printStackTrace();
  }

  private static String getCallerDescriptor(Throwable e) {
    StackTraceElement[] stackTrace = e.getStackTrace();
    StackTraceElement caller = stackTrace[stackTrace.length - 2];
    return caller.getClassName() + "." + caller.getMethodName();
  }
}
