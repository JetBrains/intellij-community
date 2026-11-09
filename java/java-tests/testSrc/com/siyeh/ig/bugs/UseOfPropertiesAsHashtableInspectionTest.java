// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class UseOfPropertiesAsHashtableInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new UseOfPropertiesAsHashtableInspection());

  @Test
  public void testProperties() {
    inspectionFixture.get().doMemberTest("""
                   public void testThis(java.util.Properties p, java.util.Properties p2, java.util.Map m, java.util.Map<String, String> m2) {
                     p./*Call to 'Hashtable.get()' on properties object*/get/**/("foo");
                     p.getProperty("foo");
                     p./*Call to 'Hashtable.put()' on properties object*/put/**/("foo", "bar");
                     p.setProperty("foo", "bar");
                     p./*Call to 'Hashtable.putAll()' on properties object*/putAll/**/(m);
                     p.putAll(m2);
                     p.putAll(p2);
                   }
                   """);
  }

  @Test
  public void testUseOfPropertiesAsHashtable() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFixAll();
  }
}
