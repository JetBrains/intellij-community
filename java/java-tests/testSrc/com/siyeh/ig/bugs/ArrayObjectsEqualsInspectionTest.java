// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class ArrayObjectsEqualsInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new ArrayObjectsEqualsInspection(), getEnvironmentClasses());

  @Test
  public void testArrayObjectsEquals() {
    inspectionFixture.get().doTest();
  }

  private static String[] getEnvironmentClasses() {
    return new String[]{
      "package java.util;" +
      "public class Objects {" +
      "    public static boolean equals(Object a, Object b) {" +
      "        return (a == b) || (a != null && a.equals(b));" +
      "    }" +
      "    public static boolean deepEquals(Object a, Object b) {" +
      "        if (a == b)" +
      "            return true;" +
      "        else if (a == null || b == null)" +
      "            return false;" +
      "        else" +
      "            return Arrays.deepEquals0(a, b);" +
      "    }" +
      "}",
      "package java.util;" +
      "public class Arrays {" +
      "    public static boolean equals(Object[] a, Object[] a2) {" +
      "        if (a==a2)" +
      "            return true;" +
      "        if (a==null || a2==null)" +
      "            return false;" +
      "        int length = a.length;" +
      "        if (a2.length != length)" +
      "            return false;" +
      "        for (int i=0; i<length; i++) {" +
      "            if (!Objects.equals(a[i], a2[i]))" +
      "                return false;" +
      "        }" +
      "        return true;" +
      "    }" +
      "    public static int hashCode(Object[] a) {" +
      "        if (a == null)" +
      "            return 0;" +
      "        int result = 1;" +
      "        for (Object element : a)" +
      "            result = 31 * result + (element == null ? 0 : element.hashCode());" +
      "        return result;" +
      "    }" +
      "}"
    };
  }
}