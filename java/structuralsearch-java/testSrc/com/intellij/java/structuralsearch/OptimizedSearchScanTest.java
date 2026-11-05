// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.structuralsearch;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.structuralsearch.StructuralSearchTestFixture;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.intellij.structuralsearch.StructuralSearchTestFixtureKt.structuralSearchFixture;
import static com.intellij.testFramework.JavaCodeInsightFixtureKt.setUpJdk;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.disposableFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.moduleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Maxim.Mossienko
 */
@TestApplication
public class OptimizedSearchScanTest {
  private static final TestFixture<Disposable> disposableFixture = disposableFixture();
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);
  private static final TestFixture<Module> moduleFixture = moduleFixture(projectFixture);
  private final TestFixture<StructuralSearchTestFixture> ssrFixture = structuralSearchFixture(projectFixture);

  @BeforeAll
  static void setUpJdk11() {
    EdtTestUtil.runInEdtAndWait(() -> setUpJdk(LanguageLevel.JDK_11, projectFixture.get(), moduleFixture.get(), disposableFixture.get()));
  }

  @Test
  public void testClassByQName() {
    doTest("A.f", "[in code:f]");
  }

  @Test
  public void testOptionalMethodWithThrowsClause() {
    doTest("exception should not be in plan",
           "class C {" +
           "    void '_m{0,1} () throws OMGWTFBBQException {}" +
           "}",
           "[in code:C][in code:class|in code:enum|in code:interface|in code:record]");

    doTest("throws should not be in plan",
           "class C {" +
           "  String m() throws '_E{0,1} {" +
           "    System.out.println();" +
           "    return null;" +
           "  }" +
           "}",
           "[in code:C][in code:class|in code:enum|in code:interface|in code:record][in code:m][in code:String][in code:println][in code:out]" +
           "[in code:System][in code:return][in code:null]");
  }

  @Test
  public void testExtendsImplements() {
    doTest("extends should not be in plan",
           "class A extends '_B{0,0} {}", "[in code:A][in code:class|in code:enum|in code:interface|in code:record]");

    doTest("implements should not be in plan",
           "class B implements '_I{0,0} {}", "[in code:B][in code:class|in code:enum|in code:interface|in code:record]");
  }

  @Test
  public void testLambda() {
    doTest(":: in plan", "'_Q::x", "[in code:x][in code:::]");
    doTest("-> in plan", "() -> {}", "[in code:->]");
  }

  @Test
  public void testRegExpChar() {
    doTest("regex should not be in plan", "'x:[regex( a+ )]", "");
    doTest("regex should not be in plan 2", "'x:[ regex(a}) ]", "");
    doTest("plain text should be in plan", "'x:[ regex(aa) ]", "[in code:aa]");
  }

  @Test
  public void testClasses() {
    doTest("class A {}", "[in code:A][in code:class|in code:enum|in code:interface|in code:record]");
    doTest("interface I {}", "[in code:I][in code:interface]");
    doTest("enum E {}", "[in code:E][in code:enum]");
    doTest("record R() {}", "[in code:R][in code:record]");
  }

  @Test
  public void testDescendants() {
    doTest("classes outside search scope should also be added to descendants plan",
           "class '_A:*List {}",
           "[in code:AbstractImmutableList|in code:AbstractList|in code:AbstractSequentialList|in code:ArrayList|in code:AsList" +
           "|in code:COWSubList|in code:CheckedList|in code:CheckedRandomAccessList|in code:CopiesList|in code:CopyOnWriteArrayList" +
           "|in code:EmptyList|in code:LinkedList|in code:List|in code:List12|in code:ListN|in code:RandomAccessSubList" +
           "|in code:SingletonList|in code:Stack|in code:SubList|in code:SynchronizedList|in code:SynchronizedRandomAccessList" +
           "|in code:UnmodifiableList|in code:UnmodifiableRandomAccessList|in code:Vector]" +
           "[in code:class|in code:enum|in code:interface|in code:record]");

    doTest("non-existing class name should be added to plan", "enum '_E:*Zyxwvuts {}", "[in code:Zyxwvuts][in code:enum]");
  }

 @Test
 public void testQualifiedReference() {
   doTest("new java.lang.RuntimeException('_x)", "[in code:new][in code:RuntimeException]");
   doTest("new java.lang.reflect.InvocationTargetException('_x)",
          "[in code:new][in code:InvocationTargetException][in code:reflect][in code:lang][in code:java]");
 }

 @Test
 public void testTryWithoutCatch() {
   doTest("try {" +
          "  '_st*;" +
          "} catch ('_Type '_exception{0,0}) {" +
          "  '_st2*;" +
          "}",
          "[in code:try]");
 }

 @Test
 public void testComment() {
   doTest("/* one/two (3|4|5) */", "[in comments:one][in comments:two][in comments:3][in comments:4][in comments:5]");
 }

 @Test
 public void testPackageLocal() {
   doTest("@Modifier(\"packageLocal\") '_FieldType '_Field = '_Init?;", "");
 }

 @Test
 public void testLiterals() {
   doTest("assert '_exp != null && true: \"'_exp is null\";",
          "[in literals:null][in literals:is][in code:assert][in code:null][in code:true]");
 }

 @Test
 public void testStringLiteral() {
   doTest("\"asd fasdf\\\\n\"", "[in literals:fasdf][in literals:asd]");
 }

 @Test
 public void testFullyQualifiedMethodName() {
    doTest("pkg.Tmp3.id()", "[in code:id]");
 }

 @Test
 public void testClassObjectAccessExpression() {
    doTest("ArrayUtil.toObjectArray($var$, $class$.class)", "[in code:toObjectArray][in code:class]");
 }

 @Test
 public void testStaticCall() {
    doTest("java.util.List.of($x$)", "[in code:of][in code:List][in code:util][in code:java]");
 }

  private void doTest(String query, String plan) {
    assertEquals(plan, ssrFixture.get().getSearchPlan(query, JavaFileType.INSTANCE));
  }

  private void doTest(String message, String query, String plan) {
    assertEquals(plan, ssrFixture.get().getSearchPlan(query, JavaFileType.INSTANCE), message);
  }
}
