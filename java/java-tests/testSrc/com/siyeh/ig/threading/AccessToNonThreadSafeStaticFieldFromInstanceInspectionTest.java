// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.threading;

import com.intellij.codeInspection.LocalInspectionTool;
import com.siyeh.ig.LightJavaInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class AccessToNonThreadSafeStaticFieldFromInstanceInspectionTest extends LightJavaInspectionTestCase {

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[]{
      "package java.text;" +
      "import java.util.Date;" +
      "public abstract class DateFormat {" +
      "  public String format(Date d) {}" +
      "  public static DateFormat getDateInstance() {" +
      "    return null;" +
      "  }" +
      "}",
      "package java.text;" +
      "public class SimpleDateFormat extends DateFormat {" +
      "  public SimpleDateFormat(String s) {}" +
      "}"
    };
  }

  public void testSimple() {
    doTest("import java.util.Date;" +
           "import java.text.DateFormat;" +
           "import java.text.SimpleDateFormat;" +
           "class C {" +
           "    private static final SimpleDateFormat df = new SimpleDateFormat(\"yyyy-MM-dd\");" +
           "    private String s = /*Access to non-thread-safe static field 'df' of type 'java.text.SimpleDateFormat'*/df/**/.format(new Date());" +
           "}");
  }

  public void testDeepCheck() {
    doTest("import java.util.Date;" +
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

  public void testGetDateInstance() {
    doTest("import java.util.Date;" +
           "import java.text.DateFormat;" +
           "import java.text.SimpleDateFormat;" +
           "class MyClass {" +
           "  private static final DateFormat F = DateFormat.getDateInstance();" +
           "  public String getFormat() {" +
           "    return /*Access to non-thread-safe static field 'F' of type 'java.text.DateFormat'*/F/**/.format(new Date());" +
           "  }" +
           "}");
  }

  @Override
  protected LocalInspectionTool getInspection() {
    return new AccessToNonThreadSafeStaticFieldFromInstanceInspection();
  }
}