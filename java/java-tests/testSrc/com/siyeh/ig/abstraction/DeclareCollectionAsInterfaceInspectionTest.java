package com.siyeh.ig.abstraction;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class DeclareCollectionAsInterfaceInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new DeclareCollectionAsInterfaceInspection(), getEnvironmentClasses());

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