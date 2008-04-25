/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jun 7, 2002
 * Time: 8:30:35 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij;

import com.intellij.execution.junit.JUnitUtil;
import com.intellij.idea.Bombed;
import com.intellij.idea.IdeaTestUtil;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.*;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

@SuppressWarnings({"HardCodedStringLiteral"})
public class TestCaseLoader {
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

  /*
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
    if (shouldExcludeTestClass(testCaseClass)) return false;

    if (TestCase.class.isAssignableFrom(testCaseClass) || TestSuite.class.isAssignableFrom(testCaseClass)) {
      return true;
    }
    try {
      final Method suiteMethod = testCaseClass.getMethod("suite");
      if (Test.class.isAssignableFrom(suiteMethod.getReturnType()) && (suiteMethod.getModifiers() & Modifier.STATIC) != 0) {
        //System.out.println("testCaseClass = " + testCaseClass);
        return true;
      }
    } catch (NoSuchMethodException e) { }

    return JUnitUtil.isJUnit4TestClass(testCaseClass);
  }

  /*
   * Determine if we should exclude this test case.
   */
  private boolean shouldExcludeTestClass(Class testCaseClass) {
    return !myTestClassesFilter.matches(testCaseClass.getName(), myTestGroupName) || isBombed(testCaseClass);
  }

  public static boolean isBombed(final Method method) {
    final Bombed bombedAnnotation = method.getAnnotation(Bombed.class);
    if (bombedAnnotation == null) return false;
    if (IdeaTestUtil.isRotten(bombedAnnotation)) {
      String message = "Disarm the stale bomb for '" + method + "' in class '" + method.getDeclaringClass() + "'";
      System.err.println(message);
      //Assert.fail(message);
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

  public void loadTestCases(final Collection<String> classNamesIterator) {
    for (String className : classNamesIterator) {
      try {
        Class candidateClass = Class.forName(className);
        addClassIfTestCase(candidateClass);
      }
      catch (UnsatisfiedLinkError e) {
        //ignore
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
  
  private static List<String> ourRanklist = getTeamCityRankList();
  private static List<String> getTeamCityRankList() {
    final String filepath = System.getProperty("teamcity.tests.recentlyFailedTests.file", null);
    if (filepath == null) {
      return Collections.emptyList();
    }

    List<String> result = new ArrayList<String>();
    try {
      BufferedReader reader = new BufferedReader(new FileReader(filepath));
      do {
        final String classname = reader.readLine();
        if (classname == null) break;
        result.add(classname);
      }
      while (true);
      return result;
    }
    catch (IOException e) {
      return Collections.emptyList();
    }
  }

  private static int getRank(Class aClass) {
    final String name = aClass.getName();
    if (ourRanklist.contains(name)) {
      return ourRanklist.indexOf(name);
    }
    return Integer.MAX_VALUE;
  }

  public List<Class> getClasses() {
    List<Class> result = new ArrayList<Class>(myClassList);

    if (!ourRanklist.isEmpty()) {
      Collections.sort(result, new Comparator<Class>() {
        public int compare(final Class o1, final Class o2) {
          return getRank(o1) - getRank(o2);
        }
      });
    }

    return result;
  }

}
