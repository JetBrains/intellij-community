// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.java.JavaFileElement;
import com.intellij.psi.impl.source.tree.java.MethodElement;
import com.intellij.psi.impl.source.tree.java.ParameterElement;
import com.intellij.testFramework.LeakHunter;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.ref.GCWatcher;

public class AstLeaksTest extends LightJavaCodeInsightFixtureTestCase {
  public void test_AST_should_be_on_a_soft_reference__for_changed_files_as_well() {
    final PsiFile file = myFixture.addClass("class Foo {}").getContainingFile();
    assertTrue(file.findElementAt(0) instanceof PsiKeyword);
    LeakHunter.checkLeak(file, JavaFileElement.class, e -> e.getPsi().equals(file));

    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      file.getViewProvider().getDocument().insertString(0, " ");
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    });
    assertTrue(file.findElementAt(0) instanceof PsiWhiteSpace);
    LeakHunter.checkLeak(file, JavaFileElement.class, e -> e.getPsi().equals(file));
  }

  public void test_super_methods_held_via_their_signatures_in_class_user_data() {
    final PsiClass superClass = myFixture.addClass("class Super { void foo() {} }");
    assertNotNull(superClass.getText()); // load AST

    PsiFile file = myFixture.addFileToProject("Main.java", "class Main extends Super { void foo() { System.out.println(\"hello\"); } }");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.doHighlighting();

    PsiClass mainClass = ((PsiJavaFile)file).getClasses()[0];
    LeakHunter.checkLeak(mainClass, MethodElement.class, node -> superClass.equals(node.getPsi().getParent()));
  }

  public void test_no_hard_refs_to_AST_after_highlighting() {
    final PsiFile sup = myFixture.addFileToProject("sup.java", "class Super { Super() {} }");
    assertNotNull(sup.findElementAt(0));// load AST
    assertNull(((PsiFileImpl)sup).getStub());

    LeakHunter.checkLeak(sup, MethodElement.class, it -> it.getPsi().getContainingFile().equals(sup));

    final PsiFile foo = myFixture.addFileToProject("a.java", "class Foo extends Super { void bar() { bar(); } }");
    myFixture.configureFromExistingVirtualFile(foo.getVirtualFile());
    myFixture.doHighlighting();

    assertNull(((PsiFileImpl)foo).getStub());
    assertNotNull(((PsiFileImpl)foo).getTreeElement());

    LeakHunter.checkLeak(foo, MethodElement.class, it -> it.getPsi().getContainingFile().equals(foo));
    LeakHunter.checkLeak(sup, MethodElement.class, it -> it.getPsi().getContainingFile().equals(sup));
  }

  public void test_no_hard_refs_to_AST_via_class_reference_type() {
    final PsiClass cls = myFixture.addClass("class Foo { Object bar() {} }");
    PsiFileImpl file = (PsiFileImpl)cls.getContainingFile();
    assertNotNull(cls.getNode());
    PsiType type = cls.getMethods()[0].getReturnType();
    assertTrue(type instanceof PsiClassReferenceType);

    LeakHunter.checkLeak(type, MethodElement.class, node -> node.getPsi().equals(cls.getMethods()[0]));

    GCWatcher.tracking(cls.getNode()).ensureCollected();
    assertFalse(file.isContentsLoaded());

    assertTrue(type.equalsToText(Object.class.getName()));
    assertFalse(file.isContentsLoaded());
  }

  @SuppressWarnings("CStyleArrayDeclaration")
  public void test_no_hard_refs_to_AST_via_class_reference_type_of_c_style_array() {
    final PsiClass cls = myFixture.addClass("class Foo { static void main(String args[]) {} }");
    PsiFileImpl file = (PsiFileImpl)cls.getContainingFile();
    assertNotNull(cls.getNode());
    PsiType type = cls.getMethods()[0].getParameterList().getParameters()[0].getTypeElement().getType();
    assertTrue(type instanceof PsiClassReferenceType);

    LeakHunter.checkLeak(type, ParameterElement.class,
                         node -> node.getPsi().equals(cls.getMethods()[0].getParameterList().getParameters()[0]));

    GCWatcher.tracking(cls.getNode()).ensureCollected();
    assertFalse(file.isContentsLoaded());

    assertTrue(type.equalsToText(String.class.getName()));
    assertFalse(file.isContentsLoaded());
  }

  public void test_no_hard_refs_to_AST_via_array_component_type() {
    final PsiClass cls = myFixture.addClass("class Foo { Object[] bar() {} }");
    PsiFileImpl file = (PsiFileImpl)cls.getContainingFile();
    assertNotNull(cls.getNode());
    PsiType type = cls.getMethods()[0].getReturnType();
    assertTrue(type instanceof PsiArrayType);
    PsiType componentType = ((PsiArrayType)type).getComponentType();
    assertTrue(componentType instanceof PsiClassReferenceType);

    LeakHunter.checkLeak(type, MethodElement.class, node -> node.getPsi().equals(cls.getMethods()[0]));

    GCWatcher.tracking(cls.getNode()).ensureCollected();
    assertFalse(file.isContentsLoaded());

    assertTrue(componentType.equalsToText(Object.class.getName()));
    assertFalse(file.isContentsLoaded());
  }

  public void test_no_hard_refs_to_AST_via_generic_component_type() {
    final PsiClass cls = myFixture.addClass("class Foo { java.util.Map<String[], ? extends CharSequence> bar() {} }");
    PsiFileImpl file = (PsiFileImpl)cls.getContainingFile();
    assertNotNull(cls.getNode());
    PsiType type = cls.getMethods()[0].getReturnType();
    assertTrue(type instanceof PsiClassReferenceType);
    PsiType[] parameters = ((PsiClassReferenceType)type).getParameters();
    assertEquals(2, parameters.length);
    assertTrue(parameters[0] instanceof PsiArrayType);
    PsiType componentType = parameters[0].getDeepComponentType();
    assertTrue(componentType instanceof PsiClassReferenceType);
    assertTrue(parameters[1] instanceof PsiWildcardType);
    PsiType bound = ((PsiWildcardType)parameters[1]).getExtendsBound();
    assertTrue(bound instanceof PsiClassReferenceType);

    LeakHunter.checkLeak(type, MethodElement.class, node -> node.getPsi().equals(cls.getMethods()[0]));

    GCWatcher.tracking(cls.getNode()).ensureCollected();
    assertFalse(file.isContentsLoaded());

    assertTrue(componentType.equalsToText(String.class.getName()));
    assertTrue(bound.equalsToText(CharSequence.class.getName()));
    assertFalse(file.isContentsLoaded());
  }

  public void test_no_hard_refs_to_AST_via_annotated_type() {
    final PsiClass cls = myFixture.addClass("class Foo { Outer.@Anno Inner bar() {} } @interface Anno {}");
    PsiFileImpl file = (PsiFileImpl)cls.getContainingFile();
    assertNotNull(cls.getNode());
    PsiType type = cls.getMethods()[0].getReturnType();
    assertTrue(type instanceof PsiClassReferenceType);
    assertEquals("Outer.Inner", type.getCanonicalText(true));

    LeakHunter.checkLeak(type, MethodElement.class, node -> node.getPsi().equals(cls.getMethods()[0]));

    GCWatcher.tracking(cls.getNode()).ensureCollected();
    assertFalse(file.isContentsLoaded());

    assertEquals("Outer.Inner", type.getCanonicalText(true));
    assertFalse(file.isContentsLoaded());
  }

  public void test_no_hard_refs_to_AST_via_annotated_type_2() {
    final PsiClass cls = myFixture.addClass("class Foo { Outer<@Anno Comp>.@Anno Inner bar() {} } @interface Anno {}");
    PsiFileImpl file = (PsiFileImpl)cls.getContainingFile();
    assertNotNull(cls.getNode());
    PsiType type = cls.getMethods()[0].getReturnType();
    assertTrue(type instanceof PsiClassReferenceType);
    PsiElement qualifier = ((PsiClassReferenceType)type).getReference().getQualifier();
    assertTrue(qualifier instanceof PsiJavaCodeReferenceElement);
    PsiType nested = ((PsiJavaCodeReferenceElement)qualifier).getParameterList().getTypeArguments()[0];
    //noinspection UnusedAssignment -- help gc
    qualifier = null;
    //noinspection UnusedAssignment -- help gc
    type = null;
    assertEquals("Comp", nested.getCanonicalText(true));

    LeakHunter.checkLeak(nested, MethodElement.class, node -> node.getPsi().equals(cls.getMethods()[0]));

    GCWatcher.tracking(cls.getNode()).ensureCollected();
    assertFalse(file.isContentsLoaded());

    assertEquals("Comp", nested.getCanonicalText(true));
    assertFalse(file.isContentsLoaded());
  }
}
