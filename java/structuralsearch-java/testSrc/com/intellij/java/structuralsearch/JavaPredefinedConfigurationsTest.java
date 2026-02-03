// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.structuralsearch;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Disposer;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.JavaPredefinedConfigurations;
import com.intellij.structuralsearch.PredefinedConfigurationsTestCase;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.testFramework.IdeaTestUtil;
import org.intellij.lang.annotations.Language;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Bas Leijdekkers
 */
public class JavaPredefinedConfigurationsTest extends PredefinedConfigurationsTestCase {
  private final Disposable myBeforeParentDisposeDisposable = Disposer.newDisposable();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    IdeaTestUtil.setProjectLanguageLevel(getProject(), LanguageLevel.JDK_16, myBeforeParentDisposeDisposable);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Disposer.dispose(myBeforeParentDisposeDisposable);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk18();
  }

  public void testAll() {
    final Configuration[] templates = JavaPredefinedConfigurations.createPredefinedTemplates();
    final Map<String, Configuration> configurationMap = Stream.of(templates).collect(Collectors.toMap(Configuration::getName, x -> x));
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.logging.without.if")),
           """
             /** @noinspection ALL*/
             class X {
               void x(String s) {
                 LOG.debug("s1: " + s);
                 if (LOG.isDebug()) {
                   LOG.debug("s2: " + s);
                 }
               }
             }""",
           "LOG.debug(\"s1: \" + s);");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.method.calls")),
           """
             /** @noinspection ALL*/
             class X {
               void x() {
                 System.out.println();
                 System.out.println(1);
                 x();
               }
             }""",
           "System.out.println()", "System.out.println(1)", "x()");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.constructors.of.the.class")),
           """
             class X {
               X() {}
               X(int i) {
                 System.out.println(i);
               }
               X(String... ss) {}
               void m() {}
             }""",
           "X() {}",
           """
             X(int i) {
                 System.out.println(i);
               }""",
           "X(String... ss) {}");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.class.with.parameterless.constructors")),
           """
             class X {
               X() {}
             }
             class Y {
               Y() {}
               Y(String name) {}
             }
             class Z {
               Z(String name) {}
             }
             class A {
               void x(String names) {}
             }""",
           """
             class X {
               X() {}
             }""",
           """
             class A {
               void x(String names) {}
             }""");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.try.without.resources")),
           """
             /** @noinspection ALL*/
             class X {{
               try {} finally {}
               try {
                 ;
               } catch (RuntimeException e) {}
               try (resourceRef) {}
               try (AutoCloseable ac = null) {}
             }}""",
           "try {} finally {}");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.switch.with.branches")),
           """
             /** @noinspection ALL*/
             class X {{
               switch (1) {
                 case 1:
                   break;
                 case 2:
                   System.out.println();
                 case 3:
                 default:
               }
               switch (1) {
                 case 1:
                  break;
                 case 2:
                   System.out.println();
                 case 3:
                 case 4:
                 default:
               }
             }}""",
           """
             switch (1) {
                 case 1:
                   break;
                 case 2:
                   System.out.println();
                 case 3:
                 default:
               }""");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.string.concatenations")),
           """
             class X {
               String s = "1" + "2" + "3" + "4" + "5" + "6" + "7" + "8" + "9" + "10" + "11";
               String t = "1" + "2" + "3" + "4" + "5" + "6" + "7" + "8" + "9"+ "10";
             }""",
           "\"1\" + \"2\" + \"3\" + \"4\" + \"5\" + \"6\" + \"7\" + \"8\" + \"9\" + \"10\" + \"11\"");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.assert.without.description")),
           """
             /** @noinspection ALL*/
             class X {{
               assert true;
               assert false : false;
               assert false : "reason";
             }}""",
           "assert true;");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.labeled.break")),
           """
             /** @noinspection ALL*/
             class X {{
               break one;
               break;
               continue;
               continue here;
             }}""",
           "break one;");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.method.returns.bounded.wildcard")),
           """
             /** @noinspection ALL*/
             abstract class X {
               List<? extends Number> one() {
                 return null;
               }
               abstract List<? extends Number> ignore();
               List<?> two() {
                 return null;
               }
               <T extends Number> T three() {
                 return null;
               }
             }""",
           """
             List<? extends Number> one() {
                 return null;
               }""");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.generic.constructors")),
           """
             class X<U> {
               X() {}
               <T> X(String s) {}
               <T extends U, V> X(int i) {}
             }""",
           "<T> X(String s) {}", "<T extends U, V> X(int i) {}");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.all.methods.of.the.class.within.hierarchy")),
           "class X {}\ninterface I { void x(); }", JavaFileType.INSTANCE,
           PsiElement::getText,
           "registerNatives", "getClass", "hashCode", "equals", "clone", "toString", "notify", "notifyAll", "wait", "wait", "wait", "finalize");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.methods.with.final.parameters")),
           """
             /** @noinspection ALL*/
             class X {
               int myI;
               X(final int i) {
                 myI = i;
               }
               public void m(final int i, int j, int k) {
                 System.out.println(i);
               }
               void n() {}
               void o(String s) {}
             }""",
           """
             X(final int i) {
                 myI = i;
               }""",
           """
             public void m(final int i, int j, int k) {
                 System.out.println(i);
               }""");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.methods.of.the.class")),
           """
             abstract class X {
               X() {}
               X(String s) {}
               abstract void x();
               int x(int i) {}
               boolean x(double d, Object o) {}
             }""",
           "X() {}", "X(String s) {}", "abstract void x();", "int x(int i) {}", "boolean x(double d, Object o) {}");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.class.static.blocks")),
           """
             /** @noinspection ALL*/
             class X {
               static {}
               static {
                 System.out.println();
               }
               {
                 {}
               }
             }""",
           "static {}",
           """
             static {
                 System.out.println();
               }""");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.class.any.initialization.blocks")),
           """
             /** @noinspection ALL*/
             class X {
               static {}
               static {
                 System.out.println();
               }
               {
                 {}
               }
             }""",
           "static {}",
           """
             static {
                 System.out.println();
               }""",
           """
             {
                 {}
               }""");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.static.fields.without.final")),
           """
             class X {
               int i1 = 1;
               static int i2 = 2;
               static final int i3 = 3;
             }""",
           "static int i2 = 2;");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.annotated.fields")),
           """
             class X {
               @SuppressWarnings("All") @Deprecated
               private static final int YES = 0;
               @Deprecated
               String text = null;
               public static final int NO = 1;
             }""",
           "@SuppressWarnings(\"All\") @Deprecated\n" +
           "  private static final int YES = 0;",
           "@Deprecated\n" +
           "  String text = null;");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.javadoc.annotated.methods")),
           """
             /** @noinspection ALL*/
             class X {
               /** constructor */
               X() {}
               /** */
               void x() {}
               /** @deprecated */
               void y() {}
               /**
                * important
                * method
                * @param i  the value that will be returned
                */
                int z(int i) {
                  return i;
                }
               void a() {}
             }""",
           "/** constructor */\n" +
           "  X() {}",
           "/** */\n" +
           "  void x() {}",
           "/** @deprecated */\n" +
           "  void y() {}",
           """
             /**
                * important
                * method
                * @param i  the value that will be returned
                */
                int z(int i) {
                  return i;
                }""");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.switches")),
           """
             /** @noinspection ALL*/
             class X {{
               int i = switch (1) {
                         default -> {}
                       };
               switch (2) {
                 case 1,2:
                   break;
                 default:
               }
             }}""",
           """
             switch (1) {
                         default -> {}
                       }""",
           """
             switch (2) {
                 case 1,2:
                   break;
                 default:
               }""");
    //noinspection DanglingJavadoc
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.comments.containing.word")),
           """
             // bug
             /* bugs are here */
             /**
             * may
             * contain
             * one bug
             */
             /* buggy */
             // bug?""",
           "// bug",
           """
             /**
             * may
             * contain
             * one bug
             */""",
           "// bug?");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.all.fields.of.the.class")),
           """
             /** @noinspection ALL*/
             interface I {
               public static final String S = "";
             }
             enum E { A, B }
             /** @noinspection ALL*/
             class C extends ThreadLocal {
               private int i = 0;
             }
             """,
           "private int i = 0;",
           "private final int threadLocalHashCode = nextHashCode();",
           "private static AtomicInteger nextHashCode = new AtomicInteger();",
           "private static final int HASH_INCREMENT = 1640531527;");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.fields.of.the.class")),
           """
             /** @noinspection ALL*/
             interface I {
               public static final String S = "";
             }
             enum E { A, B }
             /** @noinspection ALL*/
             record R(int i) {  private static final int X = 1;}
             /** @noinspection ALL*/
             class C extends ThreadLocal {
               private int i = 0;
             }
             """,
           "private int i = 0;");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.records")),
           """
             class X {}
             interface I {}
             record R1(int i, int j) {}
             record R2(double a, double b) {}""",
           "record R1(int i, int j) {}",
           "record R2(double a, double b) {}");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.double.checked.locking")),
           """
             /** @noinspection ALL*/
             class X {
               private static Object o =  null;
               static Object get() {
                 if (o == null) {
                   synchronized (X.class) {
                     if (o == null) {
                       return o;
                     }
                   }
                 }
               }
             }""",
           """
             if (o == null) {
                   synchronized (X.class) {
                     if (o == null) {
                       return o;
                     }
                   }
                 }""");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.pattern.matching.instanceof")),
           """
             /** @noinspection ALL*/
             class X {
               void x(Object o) {
                 if (o instanceof String) {
                   String s = (String)s;
                   System.out.println(s);
                 }
                 if (o instanceof String s) {
                   System.out.println(s);
                 }
               }
             }""",
           "o instanceof String s");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.local.classes")),
           """
             /** @noinspection ALL*/
             class X {
               void x() {
                 System.out.println();
                 System.out.println();
                 class Y {
                   int i;
                 }
                 System.out.println();
                 System.out.println();
               }
             }""",
           """
             class Y {
                   int i;
                 }""");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.instance.fields.of.the.class")),
           """
             class X {
               int a = 1;
               int b = 2;
               static int c = 3;
             }""",
           "int a = 1;",
           "int b = 2;");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.inner.classes")),
           """
             class X {
               class Inner1 {}
               static class Inner2 {}
             }""",
           "class Inner1 {}",
           "static class Inner2 {}");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.all.inner.classes.within.hierarchy")),
           """
             class X {
               class Inner {}
             }
             class Y extends X {}""",
           "class Inner {}");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.type.var.substitutions.in.instanceof.with.generic.types")),
           """
             /** @noinspection ALL*/
             class X<T, U, V> {
               void x(Object o) {
                 System.out.println(o instanceof X<Integer, Boolean, String>);
               }
             }""",
           "Integer",
           "Boolean",
           "String");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.javadoc.annotated.fields")),
           """
             class X {
               /** comment */
               int i;
               int j;
             }""",
           "/** comment */\n  int i;");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.javadoc.tags")),
           """
             /** @noinspection ALL*/
             class X {
               /**
                * comment
                * @version 1
                */
               int i;
               int j;
             }""",
           "@noinspection", "@version");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.any.boxing")),
                                                     """
                                                       class X {
                                                         Number n = 1;
                                                       }
                                                       """,
                                                     "1");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.any.unboxing")),
           """
             class X {
               int n = Integer.valueOf(1);
             }
             """,
           "Integer.valueOf(1)");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.boxing.in.method.calls")),
           """
             class X {
               void x(Number n) {
               }
               void y() {
                 x(1);
               }
             }
             """,
           "x(1)");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.unboxing.in.method.calls")),
           """
             class X {
               void x(int n) {
               }
               void y(Integer i) {
                 x(i);
               }
             }
             """,
           "x(i)");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.unboxing.in.declarations")),
           """
             /** @noinspection ALL*/
             class X {
               private int x = Integer.valueOf(1);
               private Integer y = 1;
             }
             """,
           "private int x = Integer.valueOf(1);");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.boxing.in.declarations")),
           """
             /** @noinspection ALL*/
             class X {
               private int x = Integer.valueOf(1);
               private Integer y = 1;
             }
             """,
           "private Integer y = 1;");
    //assertTrue((templates.length - configurationMap.size()) + " of " + templates.length +
    //           " existing templates tested. Untested templates: " + configurationMap.keySet(), configurationMap.isEmpty());
  }

  protected void doTest(Configuration template, @Language("JAVA") String source, String... results) {
    doTest(template, source, JavaFileType.INSTANCE, results);
  }
}