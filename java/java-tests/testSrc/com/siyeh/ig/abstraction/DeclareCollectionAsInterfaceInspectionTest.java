package com.siyeh.ig.abstraction;

import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.JavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.JavaTestFixtures.javaTestProjectFixture;
import static com.siyeh.ig.InspectionTestFixtures.javaInspectionFixture;

@AnalysisHostTest
public class DeclareCollectionAsInterfaceInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture();
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixture(projectFixture, () -> new DeclareCollectionAsInterfaceInspection(), getEnvironmentClasses());

  @Test
  public void testDeclareCollectionAsInterface() { inspectionFixture.get().doTest(); }

  private static String[] getEnvironmentClasses() {
    return new String[] {
      "package java.util;" +
      "public class HashSet<E> extends AbstractSet<E> implements Set<E>, Cloneable, java.io.Serializable {" +
      "  public boolean add(E e) { return null; }" +
      "}"
    };
  }
}