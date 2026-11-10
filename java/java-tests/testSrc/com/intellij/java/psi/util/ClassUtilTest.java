// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.util;

import com.intellij.JavaTestUtil;
import com.intellij.ide.util.JavaAnonymousClassesHelper;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@TestApplication
public class ClassUtilTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/psi/classUtil/");
  }

  @Test
  public void testFindPsiClassByJvmName() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("ManyClasses.java");

    ReadAction.runBlocking(() -> {
      assertNotNull(ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(myFixture.getProject()), "ManyClasses"));
      assertNotNull(ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(myFixture.getProject()), "ManyClasses$1"));
      assertNotNull(ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(myFixture.getProject()), "ManyClasses$1$1"));
      assertNotNull(ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(myFixture.getProject()), "ManyClasses$1FooLocal"));
      assertNotNull(ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(myFixture.getProject()), "ManyClasses$1FooLocal$1"));
      assertNotNull(ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(myFixture.getProject()), "ManyClasses$Child"));
      assertNotNull(ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(myFixture.getProject()), "ManyClasses$Child$"));
      assertNotNull(ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(myFixture.getProject()), "ManyClasses$Ma$ked"));
      assertNotNull(ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(myFixture.getProject()), "ManyClasses$Ma$ked$Ne$ted"));
      assertNotNull(ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(myFixture.getProject()), "ManyClasses$Edge"));
      assertNotNull(ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(myFixture.getProject()), "ManyClasses$Edge$"));
      assertNotNull(ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(myFixture.getProject()), "ManyClasses$Edge$$$tu_pid_ne$s"));
      assertNotNull(ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(myFixture.getProject()), "Local"));
      assertNotNull(ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(myFixture.getProject()), "Local$Sub"));

      PsiClass local = ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(myFixture.getProject()), "Local$");
      assertNotNull(local);
      assertEquals("Local$", local.getName());

      PsiClass sub = ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(myFixture.getProject()), "Local$$Sub");
      assertNotNull(sub);
      assertEquals("Local$", ((PsiClass)sub.getParent()).getName());

      PsiClass fooLocal2 = ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(myFixture.getProject()), "ManyClasses$2FooLocal");
      assertNotNull(fooLocal2);
      assertEquals("Runnable", fooLocal2.getImplementsListTypes()[0].getClassName());
      assertEquals("ManyClasses$2FooLocal", ClassUtil.getBinaryClassName(fooLocal2));
    });
  }

  @Test
  public void testConstructorArgumentClassNames() {
    assertClassNames("ConstructorArgumentClassNames$2", "ConstructorArgumentClassNames$1");
  }

  @Test
  public void testMultipleAnonymousArguments() {
    assertClassNames("MultipleAnonymousArguments$3", "MultipleAnonymousArguments$1", "MultipleAnonymousArguments$2");
  }

  @Test
  public void testNestedAnonymousArguments() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    assertClassNames("NestedAnonymousArguments$1", "NestedAnonymousArguments$4", "NestedAnonymousArguments$3",
                     "NestedAnonymousArguments$2", "NestedAnonymousArguments$6", "NestedAnonymousArguments$5",
                     "NestedAnonymousArguments$6$1");
    ReadAction.runBlocking(() -> {
      assertNull(ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(myFixture.getProject()), "NestedAnonymousArguments$7"));
      assertNull(ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(myFixture.getProject()), "NestedAnonymousArguments$6$2"));
    });
  }

  @Test
  public void testNamedConstructorArgument() {
    assertClassNames("NamedConstructorArgument$1", "NamedConstructorArgument$2");
  }

  @Test
  public void testEnumConstantClassNames() {
    assertClassNames("EnumConstantClassNames$1", "EnumConstantClassNames$1$1");
  }

  @Test
  public void testEnumConstantArgumentClassNames() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    assertClassNames("EnumConstantArgumentClassNames$1", "EnumConstantArgumentClassNames$2",
                     "EnumConstantArgumentClassNames$2$1", "EnumConstantArgumentClassNames$3");
    ReadAction.runBlocking(() -> {
      assertNull(ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(myFixture.getProject()), "EnumConstantArgumentClassNames$4"));
      assertNull(ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(myFixture.getProject()), "EnumConstantArgumentClassNames$2$2"));
    });
  }

  @Test
  public void testAnonymousLambdaArgumentLookup() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("AnonymousLambdaArgumentLookup.java");
    ReadAction.runBlocking(() -> {
      var outerAnonymousClass = PsiTreeUtil.findChildOfType(myFixture.getFile(), PsiAnonymousClass.class);
      assertNotNull(outerAnonymousClass);
      assertEquals("Outer", outerAnonymousClass.getBaseClassReference().getReferenceName());
      assertSame(outerAnonymousClass, ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(myFixture.getProject()), "AnonymousLambdaArgumentLookup$1"));
    });
  }

  @Test
  public void testAnonymousDiamondArgumentLookup() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("AnonymousDiamondArgumentLookup.java");
    ReadAction.runBlocking(() -> {
      var outerAnonymousClass = PsiTreeUtil.findChildOfType(myFixture.getFile(), PsiAnonymousClass.class);
      assertNotNull(outerAnonymousClass);
      assertEquals("Outer", outerAnonymousClass.getBaseClassReference().getReferenceName());
      assertSame(outerAnonymousClass, ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(myFixture.getProject()), "AnonymousDiamondArgumentLookup$1"));
    });
  }

  @Test
  public void testConstructorArgumentLegacyLookup() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("ConstructorArgumentClassNames.java");
    ReadAction.runBlocking(() -> {
      var classes = List.copyOf(PsiTreeUtil.findChildrenOfType(myFixture.getFile(), PsiAnonymousClass.class));
      assertThat(classes).hasSize(2);
      assertSame(classes.get(1), ClassUtil.findNonQualifiedClassByIndex("1", classes.getFirst(), false));
      assertNull(ClassUtil.findNonQualifiedClassByIndex("1", classes.getFirst(), true));
      assertSame(classes.get(1), ClassUtil.findPsiClass(PsiManager.getInstance(myFixture.getProject()), "ConstructorArgumentClassNames$2$1"));
    });
  }

  @Test
  public void testAnonymousClassWithoutContainingClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ReadAction.runBlocking(() -> {
      var expression = (PsiNewExpression)JavaPsiFacade.getElementFactory(myFixture.getProject())
        .createExpressionFromText("new Object() { Object nested = new Object() { }; }", null);
      var anonymousClass = expression.getAnonymousClass();
      assertNotNull(anonymousClass);
      assertNull(ClassUtil.getBinaryClassName(anonymousClass));
      assertNull(JavaAnonymousClassesHelper.getName(anonymousClass));
      var nestedClass = PsiTreeUtil.findChildOfType(anonymousClass, PsiAnonymousClass.class);
      assertNotNull(nestedClass);
      assertNull(ClassUtil.getBinaryClassName(nestedClass));
    });
  }

  private void assertClassNames(String... expectedNames) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    ReadAction.runBlocking(() -> {
      var classes = List.copyOf(PsiTreeUtil.findChildrenOfType(myFixture.getFile(), PsiAnonymousClass.class));
      assertEquals(List.of(expectedNames), ContainerUtil.map(classes, ClassUtil::getBinaryClassName));
      for (int i = 0; i < expectedNames.length; i++) {
        assertSame(classes.get(i), ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(myFixture.getProject()), expectedNames[i]), expectedNames[i]);
      }
    });
  }

  @NotNull
  private PsiClass createClass(@NotNull @Language("JAVA") String text) throws IncorrectOperationException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    return ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createClassFromText(text, null).getInnerClasses()[0]);
  }

  private String signature(String method) {
    return signature(method, "");
  }

  private String signature(String method, String typeParam) {
    return ReadAction.computeBlocking(() -> ClassUtil.getAsmMethodSignature(
      createClass("class Clazz" + typeParam + " { " + method + " }").getMethods()[0]
    ));
  }

  @Test
  public void testAsmMethodSignature() {
    assertEquals( "(I)V", signature("void m(int i) {}"));
    assertEquals( "(J)V", signature("void m(long i) {}"));
    assertEquals( "(B)V", signature("void m(byte i) {}"));
    assertEquals( "(Z)V", signature("void m(boolean i) {}"));
    assertEquals( "(D)V", signature("void m(double i) {}"));
    assertEquals( "(F)V", signature("void m(float i) {}"));
    assertEquals( "(C)V", signature("void m(char i) {}"));
    assertEquals( "(S)V", signature("void m(short i) {}"));
    assertEquals( "(SI)V", signature("void m(short s, int i) {}"));
    assertEquals( "([S[I)V", signature("void m(short[] s, int[] i) {}"));
    assertEquals( "(Ljava/lang/Object;)V", signature("void m(T t) {}", "<T>"));
    assertEquals( "(Ljava/lang/String;)V", signature("void m(T t) {}", "<T extends String>"));
    assertEquals( "(Ljava/lang/String;)V", signature("<T extends String> void m(T t) {}"));
    assertEquals( "([Ljava/lang/Object;)V", signature("void m(T[] t) {}", "<T>"));
    assertEquals( "(Ljava/lang/Object;Ljava/lang/String;)V", signature("void m(T t, String s) {}", "<T>"));
    assertEquals( "(Ljava/lang/String;)Ljava/lang/String;", signature("String m(String s) { return \"str\"; }"));
  }
}
