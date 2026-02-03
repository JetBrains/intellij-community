// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.assignment.AssignmentToCatchBlockParameterInspection;
import com.siyeh.ig.assignment.AssignmentToForLoopParameterInspection;
import com.siyeh.ig.assignment.AssignmentToLambdaParameterInspection;
import com.siyeh.ig.assignment.AssignmentToMethodParameterInspection;

/**
 * @author Bas Leijdekkers
 */
@SuppressWarnings("ALL")
public class ExtractParameterAsLocalVariableFixTest extends IGQuickFixesTestCase {

  @Override
  protected BaseInspection[] getInspections() {
    final AssignmentToForLoopParameterInspection inspection2 = new AssignmentToForLoopParameterInspection();
    inspection2.m_checkForeachParameters = true;
    return new BaseInspection[] {
      new AssignmentToMethodParameterInspection(),
      inspection2,
      new AssignmentToCatchBlockParameterInspection(),
      new AssignmentToLambdaParameterInspection()
    };
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {"class A {}",
      "public interface Function<T, R> {\n" +
      "    R apply(T t);" +
      "}"};
  }

  public void testLambdaWithExpressionBody() {
    doTest(
      InspectionGadgetsBundle.message("extract.parameter.as.local.variable.quickfix"),
      "class X {" +
      "  Function<A, A> f = (a) -> a/**/ = null;" +
      "}",
      "class X {" +
      "  Function<A, A> f = (a) -> {\n" +
      "    A a1 = a;\n" +
      "    return a1 = null;\n" +
      "};}"
    );
  }

  public void testSimpleMethod() {
    doTest(
      InspectionGadgetsBundle.message("extract.parameter.as.local.variable.quickfix"),
      "class X {\n" +
      "    void m(String s) {\n" +
      "        /**/s = \"hello\";//end of line comment\n" +
      "        System.out.println(s);\n" +
      "    }\n" +
      "}",
      "class X {\n" +
      "    void m(String s) {\n" +
      "        String hello = \"hello\";//end of line comment\n" +
      "        System.out.println(hello);\n" +
      "    }\n" +
      "}"
    );
  }

  public void testParenthesizedExpression() {
    doTest(InspectionGadgetsBundle.message("extract.parameter.as.local.variable.quickfix"),
           "class X {\n" +
           "    void m(int i) {\n" +
           "        (/**/i)++;\n" +
           "        System.out.println(i);\n" +
           "    }\n" +
           "}",
           "class X {\n" +
           "    void m(int i) {\n" +
           "        int j = i;\n" +
           "        (j)++;\n" +
           "        System.out.println(j);\n" +
           "    }\n" +
           "}");
  }

  public void testSimpleForeach() {
    doTest(
      InspectionGadgetsBundle.message("extract.parameter.as.local.variable.quickfix"),
      "class X {\n" +
      "    void m() {\n" +
      "        for (String s : new String[]{\"one\", \"two\", \"three\"})\n" +
      "            s/**/ = \"four\";\n" +
      "    }\n" +
      "}",

      "class X {\n" +
      "    void m() {\n" +
      "        for (String s : new String[]{\"one\", \"two\", \"three\"}) {\n" +
      "            String string = s;\n" +
      "            string = \"four\";\n" +
      "        }\n" +
      "    }\n" +
      "}"
    );
  }

  public void testSimpleCatchBlock() {
    doTest(
      InspectionGadgetsBundle.message("extract.parameter.as.local.variable.quickfix"),
      "import java.io.*;\n" +
      "class X {\n" +
      "    void m() {\n" +
      "        try (InputStream in = null) {\n" +
      "        } catch (IOException e) {\n" +
      "            e/**/ = null;\n" +
      "            System.out.println(e);\n" +
      "        }\n" +
      "    }\n" +
      "}",

      "import java.io.*;\n" +
      "class X {\n" +
      "    void m() {\n" +
      "        try (InputStream in = null) {\n" +
      "        } catch (IOException e) {\n" +
      "            IOException o = null;\n" +
      "            System.out.println(o);\n" +
      "        }\n" +
      "    }\n" +
      "}"
    );
  }

  public void testMultiCatchBlock() {
    assertQuickfixNotAvailable(
      InspectionGadgetsBundle.message("extract.parameter.as.local.variable.quickfix"),
      "import java.io.*;\n" +
      "class X {\n" +
      "    void m() {\n" +
      "        try (InputStream in = null) {\n" +
      "        } catch (IOException | RuntimeException e) {\n" +
      "            e/**/ = null;\n" +
      "            System.out.println(e);\n" +
      "        }\n" +
      "    }\n" +
      "}");
  }

    public void testJavaDoccedParameter() {
    doTest(InspectionGadgetsBundle.message("extract.parameter.as.local.variable.quickfix"),
           "class Foo {\n" +
           "    /**\n" +
           "     * @param customEnumElement name of custom element of the enumeration (attribute or method) whose values should be used to match equivalent {@code String}s.\n" +
           "     */\n" +
           "    void foo(String customEnumElement) {\n" +
           "        if (customEnumElement != null) {\n" +
           "            /**/customEnumElement = customEnumElement.trim();//my end of line comment\n" +
           "        }\n" +
           "    }\n" +
           "}",

           "class Foo {\n" +
           "    /**\n" +
           "     * @param customEnumElement name of custom element of the enumeration (attribute or method) whose values should be used to match equivalent {@code String}s.\n" +
           "     */\n" +
           "    void foo(String customEnumElement) {\n" +
           "        String enumElement = customEnumElement;\n" +
           "        if (enumElement != null) {\n" +
           "            enumElement = enumElement.trim();//my end of line comment\n" +
           "        }\n" +
           "    }\n" +
           "}");
  }

  public void testSuperCall() {
    doTest(InspectionGadgetsBundle.message("extract.parameter.as.local.variable.quickfix"),
           "class Foo {\n" +
           "    Foo(Object o ) {\n" +
           "        super();\n" +
           "        if (o != null) {\n" +
           "            /**/o = o.toString();\n" +
           "        }\n" +
           "    }\n" +
           "}",

           "class Foo {\n" +
           "    Foo(Object o ) {\n" +
           "        super();\n" +
           "        Object object = o;\n" +
           "        if (object != null) {\n" +
           "            object = object.toString();\n" +
           "        }\n" +
           "    }\n" +
           "}");
  }

  public void testCorrectVariableScope() {
    doTest(InspectionGadgetsBundle.message("extract.parameter.as.local.variable.quickfix"),
           "class Foo {\n" +
           "    public final void setValue(String label, Object value) {\n" +
           "        if (false) {\n" +
           "            /**/value = null;\n" +
           "        }\n" +
           "        System.out.println(value);\n" +
           "    }\n" +
           "    \n" +
           "}",

           "class Foo {\n" +
           "    public final void setValue(String label, Object value) {\n" +
           "        Object o = value;\n" +
           "        if (false) {\n" +
           "            o = null;\n" +
           "        }\n" +
           "        System.out.println(o);\n" +
           "    }\n" +
           "    \n" +
           "}");
  }

  public void testVarargs() {
    doTest(InspectionGadgetsBundle.message("extract.parameter.as.local.variable.quickfix"),
           "class RecordImpl<C> {\n" +
           "    public <T extends Enum<T>> Map<T, Object> fillEnumObjectMap(Map<T, Object> map, T... selectedColumns) {\n" +
           "        /**/selectedColumns = null;\n" +
           "        return map;\n" +
           "    }\n" +
           "\n" +
           "}",

           "class RecordImpl<C> {\n" +
           "    public <T extends Enum<T>> Map<T, Object> fillEnumObjectMap(Map<T, Object> map, T... selectedColumns) {\n" +
           "        T[] columns = null;\n" +
           "        return map;\n" +
           "    }\n" +
           "\n" +
           "}");
  }
}
