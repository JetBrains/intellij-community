/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jun 7, 2002
 * Time: 8:27:04 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.testFramework.TestLoggerFactory;
import com.intellij.util.ProfilingUtil;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestResult;
import junit.framework.TestSuite;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Iterator;
import java.util.List;

public class TestAll implements Test {

  static {
    Logger.setFactory(TestLoggerFactory.getInstance());
  }

  private static final Logger LOG = Logger.getInstance("#com.intellij.TestAll");

  private TestCaseLoader myTestCaseLoader;
  private long myStartTime = 0;
  private boolean myInterruptedByOutOfMemory = false;
  private boolean myInterruptedByOutOfTime = false;
  private long myLastTestStartTime = 0;
  private String myLastTestClass;
  private int myRunTests = -1;
  private boolean mySavingMemorySnapshot;
  private static final long MAX_MEMORY_SIZE = 128000000L;

  private static int SAVE_MEMORY_SNAPSHOT = 1;
  private static int START_GUARD = 2;
  private static int RUN_GC = 4;
  private static int CHECK_MEMORY = 8;
  private static int FILTER_CLASSES = 16;

  public static int ourMode = SAVE_MEMORY_SNAPSHOT | START_GUARD /*| RUN_GC | CHECK_MEMORY*/ | FILTER_CLASSES;
  private int myLastTestTestMethodCount = 0;
  public static final int MAX_FAILURE_TEST_COUNT = 150;

  public int countTestCases() {
    List classes = myTestCaseLoader.getClasses();

    int count = 0;

    for (Iterator i = classes.iterator(); i.hasNext();) {
      Class testCaseClass = (Class)i.next();
      Test test = getTest(testCaseClass);
      if (test != null) count += test.countTestCases();
    }

    return count;
  }

  private void beforeFirstTest() {
    if ((ourMode & START_GUARD) != 0) {
      Thread timeAndMemoryGuard = new Thread() {
        public void run() {
          System.out.println("Starting Time and Memory Guard");
          while (true) {
            try {
              try {
                Thread.sleep(10000);
              }
              catch (InterruptedException e) {
                e.printStackTrace();
              }
              // check for time spent on current test
              if (myLastTestStartTime != 0) {
                long currTime = System.currentTimeMillis();
                long secondsSpent = (currTime - myLastTestStartTime) / 1000L;
                Thread currentThread = getCurrentThread();
                if (!mySavingMemorySnapshot) {
                  if (secondsSpent > (IdeaTestCase.ourTestTime * myLastTestTestMethodCount)) {
                    System.out.println("Interrupting current Test (out of time)! Test class: "+ myLastTestClass +" Seconds spent = " + secondsSpent);
                    myInterruptedByOutOfTime = true;
                    if (currentThread != null) {
                      currentThread.interrupt();
                      if (!currentThread.isInterrupted()) {
                        currentThread.stop(new RuntimeException("Current Test Interrupted: OUT OF TIME!"));
                      }

                      break;
                    }
                  }
                }
              }
            }
            catch (Exception e) {
              e.printStackTrace();
            }
          }
          System.out.println("Time and Memory Guard finished.");
        }
      };
      timeAndMemoryGuard.setDaemon(true);
      timeAndMemoryGuard.start();
    }
    myStartTime = System.currentTimeMillis();
  }

  private Thread getCurrentThread() {
    if (IdeaTestCase.ourTestThread != null) {
      return IdeaTestCase.ourTestThread;
    }
    else if (LightIdeaTestCase.ourTestThread != null) {
      return LightIdeaTestCase.ourTestThread;
    }
    else {
      return null;
    }
  }

  private void addErrorMessage(TestResult testResult, String message) {
    String processedTestsMessage = myRunTests <= 0 ? "Noone test was run" : myRunTests + " tests processed";
    try {
      testResult.startTest(this);
      testResult.addError(this, new Throwable(processedTestsMessage + " before: " + message));
      testResult.endTest(this);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void run(final TestResult testResult) {
    List classes = myTestCaseLoader.getClasses();
    int totalTests = classes.size();
    for (Iterator i = classes.iterator(); i.hasNext();) {
      runNextTest(testResult, totalTests, (Class)i.next());
      if (testResult.shouldStop()) break;
    }
    tryGc(10);
  }

  private void runNextTest(final TestResult testResult, int totalTests, Class testCaseClass) {
    myRunTests++;
    if (!checkAvaliableMemory(35, testResult)) {
      testResult.stop();
      return;
    }
    if (testResult.errorCount() + testResult.failureCount() > MAX_FAILURE_TEST_COUNT) {
      addErrorMessage(testResult, "Too many errors. Tests stopped. Total " + myRunTests + " of " + totalTests + " tests run");
      testResult.stop();
      return;
    }
    if (myStartTime == 0) {
      boolean ourClassLoader = this.getClass().getClassLoader().getClass().getName().startsWith("com.intellij.");
      if (!ourClassLoader) {
        beforeFirstTest();
      }
    }
    else {
      if (myInterruptedByOutOfMemory) {
        addErrorMessage(testResult,
                        "Current Test Interrupted: OUT OF MEMORY! Class = " + myLastTestClass + " Total " + myRunTests + " of " +
                        totalTests +
                        " tests run");
        testResult.stop();
        return;
      }
      else if (myInterruptedByOutOfTime) {
        addErrorMessage(testResult,
                        "Current Test Interrupted: OUT OF TIME! Class = " + myLastTestClass + " Total " + myRunTests + " of " +
                        totalTests +
                        " tests run");
        testResult.stop();
        return;
      }
    }

    System.out.println("\nRunning " + testCaseClass.getName());
    LOG.info("Running " + testCaseClass.getName());
    final Test test = getTest(testCaseClass);

    if (test != null) {
      myLastTestClass = null;

      
      myLastTestClass = testCaseClass.getName();
      myLastTestStartTime = System.currentTimeMillis();
      myLastTestTestMethodCount = test.countTestCases();

      try {
        try {
          Thread.sleep(100);
        }
        catch (InterruptedException e) {
          e.printStackTrace();
        }
        test.run(testResult);
        try {
          final Application app = ApplicationManager.getApplication();
          if (app != null) {
            app.invokeAndWait(new Runnable() {
              public void run() {
                try {
                  app.runWriteAction(new Runnable() {
                    public void run() {
                      //todo[myakovlev] is it necessary?
                      FileDocumentManager manager = FileDocumentManager.getInstance();
                      if (manager instanceof FileDocumentManagerImpl) {
                        ((FileDocumentManagerImpl)manager).dropAllUnsavedDocuments();
                      }
                    }
                  });
                }
                catch (Throwable e) {
                  e.printStackTrace(System.err);
                }
              }
            }, ModalityState.NON_MMODAL);
          }
        }
        catch (Exception e) {
          e.printStackTrace();
        }
      }
      catch (Throwable t) {
        if (t instanceof OutOfMemoryError) {
          if ((ourMode & SAVE_MEMORY_SNAPSHOT) != 0) {
            try {
              mySavingMemorySnapshot = true;
              System.out.println("OutOfMemoryError detected. Saving memory snapshot started");
              ProfilingUtil.captureMemorySnapshot("allTests");
            }
            finally {
              System.out.println("Saving memory snapshot finished");
              mySavingMemorySnapshot = false;
            }
          }
        }
        testResult.addError(test, t);
      }
    }
  }

  private boolean checkAvaliableMemory(int neededMemory, TestResult testResult) {
    if ((ourMode & CHECK_MEMORY) == 0) return true;

    boolean possibleOutOfMemoryError = possibleOutOfMemory(neededMemory);
    if (possibleOutOfMemoryError) {
      tryGc(5);
      possibleOutOfMemoryError = possibleOutOfMemory(neededMemory);
      if (possibleOutOfMemoryError) {
        System.out.println("OutOfMemoryError: dumping memory");
        Runtime runtime = Runtime.getRuntime();
        long total = runtime.totalMemory();
        long free = runtime.freeMemory();
        String errorMessage = "Too much memory used. Total: " + total + " free: " + free + " used: " + (total - free) + "\n";
        String message = ProfilingUtil.forceCaptureMemorySnapshot("AllTestsOutOfMemory");
        if (message != null) errorMessage += message;
        addErrorMessage(testResult, errorMessage);
      }
    }
    return !possibleOutOfMemoryError;
  }

  private boolean possibleOutOfMemory(int neededMemory) {
    Runtime runtime = Runtime.getRuntime();
    long maxMemory = runtime.maxMemory();
    long realFreeMemory = runtime.freeMemory() + (maxMemory - runtime.totalMemory());
    long meg = 1024 * 1024;
    long needed = neededMemory * meg;
    boolean possibleOutOfMemoryError = realFreeMemory < needed;
    return possibleOutOfMemoryError;
  }

  private Test getTest(Class testCaseClass) {
    if ((testCaseClass.getModifiers() & Modifier.PUBLIC) == 0) return null;

    try {
      Method suiteMethod = testCaseClass.getMethod("suite", new Class[0]);
      return (Test)suiteMethod.invoke(null, new Class[0]);
    }
    catch (NoSuchMethodException e) {
      return new TestSuite(testCaseClass){
        public void addTest(Test test) {
          if (!(test instanceof TestCase))  {
            super.addTest(test);
          } else {
            Method method = findTestMethod(((TestCase)test));
            if (method == null || !TestCaseLoader.isBombed(method)) {
              super.addTest(test);
            }
          }

        }

        private Method findTestMethod(final TestCase testCase) {
          try {
            return testCase.getClass().getMethod(testCase.getName());
          }
          catch (NoSuchMethodException e1) {
            return null;
          }
        }
      };
    }
    catch (Exception e) {
      System.err.println("Failed to execute suite ()");
      e.printStackTrace();
    }

    return null;
  }

  private static String [] getClassRoots() {
    return System.getProperty("java.class.path").split(File.pathSeparator);
  }

  public TestAll(String packageRoot) throws Throwable {
    this(packageRoot, getClassRoots());
  }

  public TestAll(String packageRoot, String[] classRoots) throws IOException, ClassNotFoundException {
    myTestCaseLoader = new TestCaseLoader((ourMode & FILTER_CLASSES) != 0 ? "tests/testGroups.properties" : "");
    myTestCaseLoader.addClassIfTestCase(Class.forName("_FirstInSuiteTest"));

    for (int i = 0; i < classRoots.length; i++) {
      ClassFinder classFinder = new ClassFinder(new File(classRoots[i]), packageRoot);
      myTestCaseLoader.loadTestCases(classFinder.getClasses());
    }

    System.out.println("Number of test classes found: " + myTestCaseLoader.getClasses().size());
  }

  // [myakovlev] Do not delete - it is for debugging
  public static void tryGc(int times) {
    if ((ourMode & RUN_GC) == 0) return;

    for (int qqq = 1; qqq < times; qqq++) {
      try {
        Thread.sleep(qqq * 1000);
      }
      catch (InterruptedException e) {
        e.printStackTrace();
      }
      java.lang.System.gc();
      //long mem = Runtime.getRuntime().totalMemory();
      System.out.println("Runtime.getRuntime().totalMemory() = " + Runtime.getRuntime().totalMemory());
    }
  }
}
