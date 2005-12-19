/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jun 7, 2002
 * Time: 8:30:35 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij;

import com.intellij.idea.Bombed;
import com.intellij.idea.IdeaTestUtil;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.framework.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class TestCaseLoader {
  public static final String TEST_NAME_SUFFIX = "Test";
  /**
   * If <code>true</code> only loads tests that have a corresponding class without ``Test'' suffix.
   */
  public static boolean USE_ADVANCED_LOGIC = false;
  private final List<Class> myClassList = new ArrayList<Class>();
  private final TestClassesFilter myTestClassesFilter;
  private final String myTestGroupName;

  public TestCaseLoader(String classFilterName) {
    InputStream excludedStream = getClass().getClassLoader().getResourceAsStream(classFilterName);
    if (excludedStream != null) {
      try {
        myTestClassesFilter = TestClassesFilter.createOn(new InputStreamReader(excludedStream));
      }
      finally {
        try {
          excludedStream.close();
        }
        catch (IOException e) {
          e.printStackTrace();
        }
      }

    }
    else {
      myTestClassesFilter = TestClassesFilter.EMPTY_CLASSES_FILTER;
    }

    myTestGroupName = System.getProperty("idea.test.group");

    System.out.println("Using test group: [" + (myTestGroupName == null ? "" :  myTestGroupName) + "]");
  }

  /**
   * Adds <code>testCaseClass</code> to the list of classdes
   * if the class is a test case we wish to load. Calls
   * <code>shouldLoadTestCase ()</code> to determine that.
   */
  void addClassIfTestCase(final Class testCaseClass) {
    if (shouldAddTestCase(testCaseClass)) {
      myClassList.add(testCaseClass);
    }
  }

  /**
   * Determine if we should load this test case.
   */
  private boolean shouldAddTestCase(final Class testCaseClass) {
    if ((testCaseClass.getModifiers() & Modifier.ABSTRACT) != 0) return false;
    boolean shouldAdd = false;
    if ((TestCase.class.isAssignableFrom(testCaseClass) || TestSuite.class.isAssignableFrom(testCaseClass)) &&
        testCaseClass.getName().endsWith(TEST_NAME_SUFFIX)) {
      if (shouldExcludeTestClass(testCaseClass)) return false;
      if (USE_ADVANCED_LOGIC) {
        String name = testCaseClass.getName().substring(0, testCaseClass.getName().length() - TEST_NAME_SUFFIX.length());
        try {
          Class.forName(name);
          shouldAdd = true;
        }
        catch (ClassNotFoundException ignored) {
        }
      }
      else {
        shouldAdd = true;
      }
    }
    return shouldAdd;
  }

  /**
   * Determine if we should exclude this test case.
   */
  private boolean shouldExcludeTestClass(Class testCaseClass) {
    if (!myTestClassesFilter.matches(testCaseClass.getName(), myTestGroupName)) {
     return true;
    }
    return isBombed(testCaseClass);
  }

  public static boolean isBombed(final Method method) {
    final Bombed bombedAnnotation = method.getAnnotation(Bombed.class);
    if (bombedAnnotation == null) return false;
    if (IdeaTestUtil.isRotten(bombedAnnotation)) {
      String message = "Disarm the stale bomb for '" + method + "' in class '" + method.getDeclaringClass() + "'";
      System.err.println(message);
      Assert.fail(message);
    }
    return !IdeaTestUtil.bombExplodes(bombedAnnotation);
  }

  public static boolean isBombed(final Class<?> testCaseClass) {
    final Bombed bombedAnnotation = testCaseClass.getAnnotation(Bombed.class);
    if (bombedAnnotation == null) return false;
    if (IdeaTestUtil.isRotten(bombedAnnotation)) {
      String message = "Disarm the stale bomb for '" + testCaseClass + "'";
      System.err.println(message);
     // Assert.fail(message);
    }
    return !IdeaTestUtil.bombExplodes(bombedAnnotation);
  }

  public void loadTestCases(final Iterator classNamesIterator) {
    while (classNamesIterator.hasNext()) {
      String className = (String)classNamesIterator.next();
      try {
        Class candidateClass = Class.forName(className);
        addClassIfTestCase(candidateClass);
      }
      catch (ClassNotFoundException e) {
        e.printStackTrace();
        System.err.println("Cannot load class: " + className + " " + e.getMessage());
      }
      catch (NoClassDefFoundError e) {
        e.printStackTrace();
        System.err.println("Cannot load class that " + className + " is dependant on");
      }
      catch (ExceptionInInitializerError e) {
        e.printStackTrace();
        e.getException().printStackTrace();
        System.err.println("Cannot load class: " + className + " " + e.getException().getMessage());
      }
    }
  }

  public List getClasses() {
    return Collections.unmodifiableList(myClassList);
  }

}
