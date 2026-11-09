// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.threading;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
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
public class AccessToNonThreadSafeStaticFieldFromInstanceInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new AccessToNonThreadSafeStaticFieldFromInstanceInspection());

  @Test
  public void testSimple() {
    inspectionFixture.get().doTest("import java.util.Date;" +
           "import java.text.DateFormat;" +
           "import java.text.SimpleDateFormat;" +
           "class C {" +
           "    private static final SimpleDateFormat df = new SimpleDateFormat(\"yyyy-MM-dd\");" +
           "    private String s = /*Access to non-thread-safe static field 'df' of type 'java.text.SimpleDateFormat'*/df/**/.format(new Date());" +
           "}");
  }

  @Test
  public void testDeepCheck() {
    inspectionFixture.get().doTest("import java.util.Date;" +
           "import java.text.DateFormat;" +
           "import java.text.SimpleDateFormat;" +
           "class C {" +
           "    private static final Object df = new SimpleDateFormat(\"yyyy-MM-dd\");" +
           "    private static final Date d = new Date();" +
           "    private static final String s1 = ((DateFormat)df).format(d);" +
           "    String m() {" +
           "      return ((DateFormat)/*Access to non-thread-safe static field 'df' of type 'java.text.SimpleDateFormat'*/df/**/).format(d);" +
           "    }" +
           "}");
  }

  @Test
  public void testGetDateInstance() {
    inspectionFixture.get().doTest("import java.util.Date;" +
           "import java.text.DateFormat;" +
           "import java.text.SimpleDateFormat;" +
           "class MyClass {" +
           "  private static final DateFormat F = DateFormat.getDateInstance();" +
           "  public String getFormat() {" +
           "    return /*Access to non-thread-safe static field 'F' of type 'java.text.DateFormat'*/F/**/.format(new Date());" +
           "  }" +
           "}");
  }
}