// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.cache;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.PsiJavaFileImpl;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class TypeInfoTest extends LightJavaCodeInsightFixtureTestCase {
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

  public void testNoAnnotations() {
    doTest("int");
  }

  public void testArray() {
    doTest("int @Anno []");
  }
  
  public void testGeneric() {
    doTest("List<@Anno X3>");
    doTest("List<@Anno @Anno2 X3>");
  }
  
  public void testNested() {
    doTest("Outer.@Anno2 Middle.@Anno @Anno1 Inner");
  }
  
  public void testGenericBound() {
    doTest("List<? extends @Anno X3>");
    doTest("List<? super @Anno X3>");
    doTest("List<? extends @Anno X5>");
    doTest("List1<? super @Anno X3,? extends @Anno X5,@Anno X6>");
  }
  
  public void testGenericArray() {
    doTest("List<@Anno X3> @Anno2 []");
    doTest("List<@Anno X3 @Anno2 []>");
  }
  
  public void testGenericArrayBound() {
    doTest("List<@Anno ? extends @Anno1 X3 @Anno2 []>");
  }
  
  public void testGenericNested() {
    doTest("X1<@A2 X2<? extends @A3 X3>>.@A4 X4<@A5 X5,@A6 X6 @A7 []>");
  }
  
  public void testGenericNested2() {
    doTest("List1<Outer.@Anno2 Middle.@Anno @Anno1 Inner,Outer.@Anno2 Middle.@Anno1 @Anno2 Inner>");
  }

  private void doTest(String typeText) {
    String javaFile = "class X { " + typeText + " x; }\n" + USED_CLASSES;
    myFixture.configureByText("X.java", javaFile);
    LighterAST ast = ((PsiJavaFileImpl)getFile()).getTreeElement().getLighterAST();
    LighterASTNode root = ast.getRoot();
    LighterASTNode classNode = LightTreeUtil.firstChildOfType(ast, root, JavaElementType.CLASS);
    LighterASTNode fieldNode = LightTreeUtil.firstChildOfType(ast, classNode, JavaElementType.FIELD);
    TypeInfo info = TypeInfo.create(ast, fieldNode, null);
    PsiType type = getElementFactory().createTypeFromText(info.text(), getFile());
    type = info.getTypeAnnotations().applyTo(type, getFile());
    assertEquals(typeText, type.getCanonicalText(true));
  }
}