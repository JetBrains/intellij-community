// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.impl.cache;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.source.PsiJavaFileImpl;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class TypeInfoTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private static final String USED_CLASSES = """
    class X1<T>{
      class X4<K,V>{}
    }
    class X2<T>{}
    class X3{}
    class X5{}
    class X6{}
    class List<T>{}
    class List1<A,B,C>{}
    class Outer{
      class Middle {
        class Inner{
        }
      }
    }
    @interface Anno{}
    @interface Anno1{}
    @interface Anno2{}
    @interface A2{}
    @interface A3{}
    @interface A4{}
    @interface A5{}
    @interface A6{}
    @interface A7{}""";

  @Test
  public void testNoAnnotations() {
    doTest("int");
  }

  @Test
  public void testArray() {
    doTest("int @Anno []");
  }
  
  @Test
  public void testGeneric() {
    doTest("List<@Anno X3>");
    doTest("List<@Anno @Anno2 X3>");
  }
  
  @Test
  public void testNested() {
    doTest("Outer.@Anno2 Middle.@Anno @Anno1 Inner");
  }
  
  @Test
  public void testGenericBound() {
    doTest("List<? extends @Anno X3>");
    doTest("List<? super @Anno X3>");
    doTest("List<? extends @Anno X5>");
    doTest("List1<? super @Anno X3,? extends @Anno X5,@Anno X6>");
  }
  
  @Test
  public void testGenericArray() {
    doTest("List<@Anno X3> @Anno2 []");
    doTest("List<@Anno X3 @Anno2 []>");
  }
  
  @Test
  public void testGenericArrayBound() {
    doTest("List<@Anno ? extends @Anno1 X3 @Anno2 []>");
  }
  
  @Test
  public void testGenericNested() {
    doTest("X1<@A2 X2<? extends @A3 X3>>.@A4 X4<@A5 X5,@A6 X6 @A7 []>");
  }
  
  @Test
  public void testGenericNested2() {
    doTest("List1<Outer.@Anno2 Middle.@Anno @Anno1 Inner,Outer.@Anno2 Middle.@Anno1 @Anno2 Inner>");
  }

  private void doTest(String typeText) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String javaFile = "class X { " + typeText + " x; }\n" + USED_CLASSES;
    myFixture.configureByText("X.java", javaFile);
    ReadAction.runBlocking(() -> {
      LighterAST ast = ((PsiJavaFileImpl)myFixture.getFile()).getTreeElement().getLighterAST();
      LighterASTNode root = ast.getRoot();
      LighterASTNode classNode = LightTreeUtil.firstChildOfType(ast, root, JavaElementType.CLASS);
      LighterASTNode fieldNode = LightTreeUtil.firstChildOfType(ast, classNode, JavaElementType.FIELD);
      TypeInfo info = TypeInfo.create(ast, fieldNode, null);
      PsiType type = JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText(info.annotatedText(), myFixture.getFile());
      assertEquals(typeText, type.getCanonicalText(true));
    });
  }
}