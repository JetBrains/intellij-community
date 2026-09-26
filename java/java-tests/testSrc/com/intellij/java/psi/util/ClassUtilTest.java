// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.util;

import com.intellij.JavaTestUtil;
import com.intellij.ide.util.JavaAnonymousClassesHelper;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ClassUtilTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/psi/classUtil/";
  }

  public void testFindPsiClassByJvmName() {
    myFixture.configureByFile("ManyClasses.java");

    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses$1"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses$1$1"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses$1FooLocal"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses$1FooLocal$1"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses$Child"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses$Child$"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses$Ma$ked"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses$Ma$ked$Ne$ted"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses$Edge"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses$Edge$"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses$Edge$$$tu_pid_ne$s"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "Local"));
    assertNotNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "Local$Sub"));

    PsiClass local = ClassUtil.findPsiClassByJVMName(getPsiManager(), "Local$");
    assertNotNull(local);
    assertEquals("Local$", local.getName());

    PsiClass sub = ClassUtil.findPsiClassByJVMName(getPsiManager(), "Local$$Sub");
    assertNotNull(sub);
    assertEquals("Local$", ((PsiClass)sub.getParent()).getName());

    PsiClass fooLocal2 = ClassUtil.findPsiClassByJVMName(getPsiManager(), "ManyClasses$2FooLocal");
    assertNotNull(fooLocal2);
    assertEquals("Runnable", fooLocal2.getImplementsListTypes()[0].getClassName());
    assertEquals("ManyClasses$2FooLocal", ClassUtil.getBinaryClassName(fooLocal2));
  }

  public void testConstructorArgumentClassNames() {
    assertClassNames("ConstructorArgumentClassNames$2", "ConstructorArgumentClassNames$1");
  }

  public void testMultipleAnonymousArguments() {
    assertClassNames("MultipleAnonymousArguments$3", "MultipleAnonymousArguments$1", "MultipleAnonymousArguments$2");
  }

  public void testNestedAnonymousArguments() {
    assertClassNames("NestedAnonymousArguments$1", "NestedAnonymousArguments$4", "NestedAnonymousArguments$3",
                     "NestedAnonymousArguments$2", "NestedAnonymousArguments$6", "NestedAnonymousArguments$5",
                     "NestedAnonymousArguments$6$1");
    assertNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "NestedAnonymousArguments$7"));
    assertNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "NestedAnonymousArguments$6$2"));
  }

  public void testNamedConstructorArgument() {
    assertClassNames("NamedConstructorArgument$1", "NamedConstructorArgument$2");
  }

  public void testEnumConstantClassNames() {
    assertClassNames("EnumConstantClassNames$1", "EnumConstantClassNames$1$1");
  }

  public void testEnumConstantArgumentClassNames() {
    assertClassNames("EnumConstantArgumentClassNames$1", "EnumConstantArgumentClassNames$2",
                     "EnumConstantArgumentClassNames$2$1", "EnumConstantArgumentClassNames$3");
    assertNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "EnumConstantArgumentClassNames$4"));
    assertNull(ClassUtil.findPsiClassByJVMName(getPsiManager(), "EnumConstantArgumentClassNames$2$2"));
  }

  public void testAnonymousLambdaArgumentLookup() {
    myFixture.configureByFile("AnonymousLambdaArgumentLookup.java");
    var outerAnonymousClass = PsiTreeUtil.findChildOfType(myFixture.getFile(), PsiAnonymousClass.class);
    assertNotNull(outerAnonymousClass);
    assertEquals("Outer", outerAnonymousClass.getBaseClassReference().getReferenceName());
    assertSame(outerAnonymousClass, ClassUtil.findPsiClassByJVMName(getPsiManager(), "AnonymousLambdaArgumentLookup$1"));
  }

  public void testAnonymousDiamondArgumentLookup() {
    myFixture.configureByFile("AnonymousDiamondArgumentLookup.java");
    var outerAnonymousClass = PsiTreeUtil.findChildOfType(myFixture.getFile(), PsiAnonymousClass.class);
    assertNotNull(outerAnonymousClass);
    assertEquals("Outer", outerAnonymousClass.getBaseClassReference().getReferenceName());
    assertSame(outerAnonymousClass, ClassUtil.findPsiClassByJVMName(getPsiManager(), "AnonymousDiamondArgumentLookup$1"));
  }

  public void testConstructorArgumentLegacyLookup() {
    myFixture.configureByFile("ConstructorArgumentClassNames.java");
    var classes = List.copyOf(PsiTreeUtil.findChildrenOfType(myFixture.getFile(), PsiAnonymousClass.class));
    assertSize(2, classes);
    assertSame(classes.get(1), ClassUtil.findNonQualifiedClassByIndex("1", classes.getFirst(), false));
    assertNull(ClassUtil.findNonQualifiedClassByIndex("1", classes.getFirst(), true));
    assertSame(classes.get(1), ClassUtil.findPsiClass(getPsiManager(), "ConstructorArgumentClassNames$2$1"));
  }

  public void testAnonymousClassWithoutContainingClass() {
    var expression = (PsiNewExpression)JavaPsiFacade.getElementFactory(getProject())
      .createExpressionFromText("new Object() { Object nested = new Object() { }; }", null);
    var anonymousClass = expression.getAnonymousClass();
    assertNotNull(anonymousClass);
    assertNull(ClassUtil.getBinaryClassName(anonymousClass));
    assertNull(JavaAnonymousClassesHelper.getName(anonymousClass));
    var nestedClass = PsiTreeUtil.findChildOfType(anonymousClass, PsiAnonymousClass.class);
    assertNotNull(nestedClass);
    assertNull(ClassUtil.getBinaryClassName(nestedClass));
  }

  private void assertClassNames(String... expectedNames) {
    myFixture.configureByFile(getTestName(false) + ".java");
    var classes = List.copyOf(PsiTreeUtil.findChildrenOfType(myFixture.getFile(), PsiAnonymousClass.class));
    assertEquals(List.of(expectedNames), ContainerUtil.map(classes, ClassUtil::getBinaryClassName));
    for (int i = 0; i < expectedNames.length; i++) {
      assertSame(expectedNames[i], classes.get(i), ClassUtil.findPsiClassByJVMName(getPsiManager(), expectedNames[i]));
    }
  }

  @NotNull
  private PsiClass createClass(@NotNull @Language("JAVA") String text) throws IncorrectOperationException {
    return JavaPsiFacade.getElementFactory(getProject()).createClassFromText(text, null).getInnerClasses()[0];
  }

  private String signature(String method) {
    return signature(method, "");
  }

  private String signature(String method, String typeParam) {
    return ClassUtil.getAsmMethodSignature(
      createClass("class Clazz" + typeParam + " { " + method + " }").getMethods()[0]
    );
  }

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
