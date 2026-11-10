// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.codeInsight.Nullability;
import com.intellij.java.codeInsight.JSpecifyTestUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEllipsisType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.PsiWildcardType;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.java.JavaFileElement;
import com.intellij.psi.impl.source.tree.java.MethodElement;
import com.intellij.psi.impl.source.tree.java.ParameterElement;
import com.intellij.testFramework.LeakHunter;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ref.GCWatcher;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class AstLeaksTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void test_AST_should_be_on_a_soft_reference__for_changed_files_as_well() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiFile file = myFixture.addClass("class Foo {}").getContainingFile();
    ReadAction.runBlocking(() -> assertTrue(file.findElementAt(0) instanceof PsiKeyword));
    LeakHunter.checkLeak(file, JavaFileElement.class, e -> e.getPsi().equals(file));

    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      file.getViewProvider().getDocument().insertString(0, " ");
      PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
    });
    ReadAction.runBlocking(() -> assertTrue(file.findElementAt(0) instanceof PsiWhiteSpace));
    LeakHunter.checkLeak(file, JavaFileElement.class, e -> e.getPsi().equals(file));
  }

  @Test
  public void test_super_methods_held_via_their_signatures_in_class_user_data() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiClass superClass = myFixture.addClass("class Super { void foo() {} }");
    ReadAction.runBlocking(() -> assertNotNull(superClass.getText())); // load AST

    PsiFile file = myFixture.addFileToProject("Main.java", "class Main extends Super { void foo() { System.out.println(\"hello\"); } }");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.doHighlighting();

    PsiClass mainClass = ReadAction.computeBlocking(() -> ((PsiJavaFile)file).getClasses()[0]);
    LeakHunter.checkLeak(mainClass, MethodElement.class, node -> superClass.equals(node.getPsi().getParent()));
  }

  @Test
  public void test_no_hard_refs_to_AST_after_highlighting() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiFile sup = myFixture.addFileToProject("sup.java", "class Super { Super() {} }");
    ReadAction.runBlocking(() -> {
      assertNotNull(sup.findElementAt(0));// load AST
      assertNull(((PsiFileImpl)sup).getStub());
    });

    LeakHunter.checkLeak(sup, MethodElement.class, it -> it.getPsi().getContainingFile().equals(sup));

    final PsiFile foo = myFixture.addFileToProject("a.java", "class Foo extends Super { void bar() { bar(); } }");
    myFixture.configureFromExistingVirtualFile(foo.getVirtualFile());
    myFixture.doHighlighting();

    ReadAction.runBlocking(() -> {
      assertNull(((PsiFileImpl)foo).getStub());
      assertNotNull(((PsiFileImpl)foo).getTreeElement());
    });

    LeakHunter.checkLeak(foo, MethodElement.class, it -> it.getPsi().getContainingFile().equals(foo));
    LeakHunter.checkLeak(sup, MethodElement.class, it -> it.getPsi().getContainingFile().equals(sup));
  }

  @Test
  public void test_no_hard_refs_to_AST_via_class_reference_type() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiClass cls = myFixture.addClass("class Foo { Object bar() {} }");
    PsiFileImpl file = ReadAction.computeBlocking(() -> (PsiFileImpl)cls.getContainingFile());
    ReadAction.runBlocking(() -> assertNotNull(cls.getNode()));
    PsiType type = ReadAction.computeBlocking(() -> cls.getMethods()[0].getReturnType());
    assertTrue(type instanceof PsiClassReferenceType);

    LeakHunter.checkLeak(type, MethodElement.class, node -> node.getPsi().equals(cls.getMethods()[0]));

    ReadAction.runBlocking(() -> {
      GCWatcher.tracking(cls.getNode()).ensureCollected();
      assertFalse(file.isContentsLoaded());

      assertTrue(type.equalsToText(Object.class.getName()));
      assertFalse(file.isContentsLoaded());
    });
  }

  @Test
  @SuppressWarnings("CStyleArrayDeclaration")
  public void test_no_hard_refs_to_AST_via_class_reference_type_of_c_style_array() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiClass cls = myFixture.addClass("class Foo { static void main(String args[]) {} }");
    PsiFileImpl file = ReadAction.computeBlocking(() -> (PsiFileImpl)cls.getContainingFile());
    ReadAction.runBlocking(() -> assertNotNull(cls.getNode()));
    PsiType type = ReadAction.computeBlocking(() -> cls.getMethods()[0].getParameterList().getParameters()[0].getTypeElement().getType());
    assertTrue(type instanceof PsiClassReferenceType);

    LeakHunter.checkLeak(type, ParameterElement.class,
                         node -> node.getPsi().equals(cls.getMethods()[0].getParameterList().getParameters()[0]));

    ReadAction.runBlocking(() -> {
      GCWatcher.tracking(cls.getNode()).ensureCollected();
      assertFalse(file.isContentsLoaded());

      assertTrue(type.equalsToText(String.class.getName()));
      assertFalse(file.isContentsLoaded());
    });
  }

  @Test
  public void testNullabilityCorrectLoad() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    JSpecifyTestUtil.addJSpecifyNonNull(myFixture);
    final PsiClass cls = myFixture.addClass(
      """
        @org.jspecify.annotations.NullMarked
        class Foo { 
          static void test(java.util.Map<String, ? extends java.lang.String[]> args) {} 
        }""");
    PsiFileImpl file = ReadAction.computeBlocking(() -> (PsiFileImpl)cls.getContainingFile());
    ReadAction.runBlocking(() -> assertNotNull(cls.getNode()));
    PsiType type = ReadAction.computeBlocking(() -> cls.getMethods()[0].getParameterList().getParameters()[0].getTypeElement().getType());
    assertTrue(type instanceof PsiClassReferenceType);

    LeakHunter.checkLeak(type, ParameterElement.class,
                         node -> node.getPsi().equals(cls.getMethods()[0].getParameterList().getParameters()[0]));

    ReadAction.runBlocking(() -> {
      GCWatcher.tracking(cls.getNode()).ensureCollected();
      assertFalse(file.isContentsLoaded());

      assertTrue(type.equalsToText("java.util.Map<java.lang.String,? extends java.lang.String[]>"));
      assertFalse(file.isContentsLoaded());
      PsiType targetType = ((PsiWildcardType)((PsiClassReferenceType)(type)).getParameters()[1]).getBound();
      assertTrue(targetType instanceof PsiArrayType);
      assertTrue(targetType.equalsToText("java.lang.String[]"));
      assertFalse(file.isContentsLoaded());
      assertSame(targetType.getNullability().nullability(), Nullability.NOT_NULL);
    });
  }

  @Test
  public void testNullabilityCorrectLoadGetBeforeAsymPath() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    JSpecifyTestUtil.addJSpecifyNonNull(myFixture);
    final PsiClass cls = myFixture.addClass(
      """
        @org.jspecify.annotations.NullMarked
        class Foo { 
          static void test(java.util.Map<String, java.lang.String[]> args) {} 
        }""");
    PsiFileImpl file = ReadAction.computeBlocking(() -> (PsiFileImpl)cls.getContainingFile());
    ReadAction.runBlocking(() -> assertNotNull(cls.getNode()));
    PsiType type = ReadAction.computeBlocking(() -> cls.getMethods()[0].getParameterList().getParameters()[0].getTypeElement().getType());
    assertTrue(type instanceof PsiClassReferenceType);
    //don't create intermediate variables, it is necessary to skip it for gc collection
    PsiType targetType = ReadAction.computeBlocking(() -> ((PsiTypeElement)cls.getMethods()[0].getParameterList().getParameters()[0].getTypeElement().getChildren()[0].getChildren()[3].getChildren()[4]).getType());
    LeakHunter.checkLeak(targetType, ParameterElement.class,
                         node -> node.getPsi().equals(cls.getMethods()[0].getParameterList().getParameters()[0]));

    ReadAction.runBlocking(() -> {
      GCWatcher.tracking(cls.getNode()).ensureCollected();
      assertFalse(file.isContentsLoaded());
      assertTrue(type.equalsToText("java.util.Map<java.lang.String,java.lang.String[]>"));
      assertFalse(file.isContentsLoaded());
      assertTrue(targetType instanceof PsiArrayType);
      assertTrue(targetType.equalsToText("java.lang.String[]"));
      assertFalse(file.isContentsLoaded());
      assertSame(targetType.getNullability().nullability(), Nullability.NOT_NULL);
    });
  }

  @Test
  public void testNullabilityCorrectLoadGetBefore() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    JSpecifyTestUtil.addJSpecifyNonNull(myFixture);
    final PsiClass cls = myFixture.addClass(
      """
        @org.jspecify.annotations.NullMarked
        class Foo { 
          static void test(java.util.Map<String, ? extends java.lang.String[]> args) {} 
        }""");
    PsiFileImpl file = ReadAction.computeBlocking(() -> (PsiFileImpl)cls.getContainingFile());
    ReadAction.runBlocking(() -> assertNotNull(cls.getNode()));
    PsiType type = ReadAction.computeBlocking(() -> cls.getMethods()[0].getParameterList().getParameters()[0].getTypeElement().getType());
    assertTrue(type instanceof PsiClassReferenceType);
    //don't create an intermediate variables, it is necessary to skip it for gc collection
    PsiType targetType = ReadAction.computeBlocking(() -> ((PsiTypeElement)cls.getMethods()[0].getParameterList().getParameters()[0].getTypeElement().getChildren()[0].getChildren()[3].getChildren()[4].getChildren()[4]).getType());
    LeakHunter.checkLeak(targetType, ParameterElement.class,
                         node -> node.getPsi().equals(cls.getMethods()[0].getParameterList().getParameters()[0]));

    ReadAction.runBlocking(() -> {
      GCWatcher.tracking(cls.getNode()).ensureCollected();
      assertFalse(file.isContentsLoaded());
      assertTrue(type.equalsToText("java.util.Map<java.lang.String,? extends java.lang.String[]>"));
      assertFalse(file.isContentsLoaded());
      assertTrue(targetType instanceof PsiArrayType);
      assertTrue(targetType.equalsToText("java.lang.String[]"));
      assertFalse(file.isContentsLoaded());
      assertSame(targetType.getNullability().nullability(), Nullability.NOT_NULL);
    });
  }

  @Test
  public void testNullabilityCorrectLoadEllipsis() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @Language("JAVA") String nullMarked =
      """
        package org.jspecify.annotations;
        import java.lang.annotation.*;
        @Target({ElementType.TYPE, ElementType.METHOD, ElementType.MODULE})
        public @interface NullMarked {}""";
    myFixture.addClass(nullMarked);
    final PsiClass cls = myFixture.addClass(
      """
        @org.jspecify.annotations.NullMarked
        class Foo { 
          static void test(String... args) {} 
        }""");
    PsiFileImpl file = ReadAction.computeBlocking(() -> (PsiFileImpl)cls.getContainingFile());
    ReadAction.runBlocking(() -> assertNotNull(cls.getNode()));
    PsiType type = ReadAction.computeBlocking(() -> cls.getMethods()[0].getParameterList().getParameters()[0].getTypeElement().getType());
    assertTrue(type instanceof PsiEllipsisType);

    LeakHunter.checkLeak(type, ParameterElement.class,
                         node -> node.getPsi().equals(cls.getMethods()[0].getParameterList().getParameters()[0]));

    ReadAction.runBlocking(() -> {
      GCWatcher.tracking(cls.getNode()).ensureCollected();
      assertFalse(file.isContentsLoaded());

      assertTrue(type.equalsToText("java.lang.String..."));
      assertFalse(file.isContentsLoaded());
      assertSame(type.getNullability().nullability(), Nullability.NOT_NULL);
    });
  }

  @Test
  public void test_no_hard_refs_to_AST_via_array_component_type() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiClass cls = myFixture.addClass("class Foo { Object[] bar() {} }");
    PsiFileImpl file = ReadAction.computeBlocking(() -> (PsiFileImpl)cls.getContainingFile());
    ReadAction.runBlocking(() -> assertNotNull(cls.getNode()));
    PsiType type = ReadAction.computeBlocking(() -> cls.getMethods()[0].getReturnType());
    assertTrue(type instanceof PsiArrayType);
    PsiType componentType = ReadAction.computeBlocking(() -> ((PsiArrayType)type).getComponentType());
    assertTrue(componentType instanceof PsiClassReferenceType);

    LeakHunter.checkLeak(type, MethodElement.class, node -> node.getPsi().equals(cls.getMethods()[0]));

    ReadAction.runBlocking(() -> {
      GCWatcher.tracking(cls.getNode()).ensureCollected();
      assertFalse(file.isContentsLoaded());

      assertTrue(componentType.equalsToText(Object.class.getName()));
      assertFalse(file.isContentsLoaded());
    });
  }

  @Test
  public void test_no_hard_refs_to_AST_via_generic_component_type() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiClass cls = myFixture.addClass("class Foo { java.util.Map<String[], ? extends CharSequence> bar() {} }");
    PsiFileImpl file = ReadAction.computeBlocking(() -> (PsiFileImpl)cls.getContainingFile());
    ReadAction.runBlocking(() -> assertNotNull(cls.getNode()));
    PsiType type = ReadAction.computeBlocking(() -> cls.getMethods()[0].getReturnType());
    assertTrue(type instanceof PsiClassReferenceType);
    PsiType[] parameters = ReadAction.computeBlocking(() -> ((PsiClassReferenceType)type).getParameters());
    assertEquals(2, parameters.length);
    assertTrue(parameters[0] instanceof PsiArrayType);
    PsiType componentType = ReadAction.computeBlocking(() -> parameters[0].getDeepComponentType());
    assertTrue(componentType instanceof PsiClassReferenceType);
    assertTrue(parameters[1] instanceof PsiWildcardType);
    PsiType bound = ReadAction.computeBlocking(() -> ((PsiWildcardType)parameters[1]).getExtendsBound());
    assertTrue(bound instanceof PsiClassReferenceType);

    LeakHunter.checkLeak(type, MethodElement.class, node -> node.getPsi().equals(cls.getMethods()[0]));

    ReadAction.runBlocking(() -> {
      GCWatcher.tracking(cls.getNode()).ensureCollected();
      assertFalse(file.isContentsLoaded());

      assertTrue(componentType.equalsToText(String.class.getName()));
      assertTrue(bound.equalsToText(CharSequence.class.getName()));
      assertFalse(file.isContentsLoaded());
    });
  }

  @Test
  public void test_no_hard_refs_to_AST_via_annotated_type() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiClass cls = myFixture.addClass("class Foo { Outer.@Anno Inner bar() {} } @interface Anno {}");
    PsiFileImpl file = ReadAction.computeBlocking(() -> (PsiFileImpl)cls.getContainingFile());
    ReadAction.runBlocking(() -> assertNotNull(cls.getNode()));
    PsiType type = ReadAction.computeBlocking(() -> cls.getMethods()[0].getReturnType());
    assertTrue(type instanceof PsiClassReferenceType);
    ReadAction.runBlocking(() -> assertEquals("Outer.Inner", type.getCanonicalText(true)));

    LeakHunter.checkLeak(type, MethodElement.class, node -> node.getPsi().equals(cls.getMethods()[0]));

    ReadAction.runBlocking(() -> {
      GCWatcher.tracking(cls.getNode()).ensureCollected();
      assertFalse(file.isContentsLoaded());

      assertEquals("Outer.Inner", type.getCanonicalText(true));
      assertFalse(file.isContentsLoaded());
    });
  }

  @Test
  public void test_no_hard_refs_to_AST_via_annotated_type_2() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiClass cls = myFixture.addClass("class Foo { Outer<@Anno Comp>.@Anno Inner bar() {} } @interface Anno {}");
    PsiFileImpl file = ReadAction.computeBlocking(() -> (PsiFileImpl)cls.getContainingFile());
    PsiType nested = ReadAction.computeBlocking(() -> {
      assertNotNull(cls.getNode());
      PsiType type = cls.getMethods()[0].getReturnType();
      assertTrue(type instanceof PsiClassReferenceType);
      PsiElement qualifier = ((PsiClassReferenceType)type).getReference().getQualifier();
      assertTrue(qualifier instanceof PsiJavaCodeReferenceElement);
      return ((PsiJavaCodeReferenceElement)qualifier).getParameterList().getTypeArguments()[0];
    });
    ReadAction.runBlocking(() -> assertEquals("Comp", nested.getCanonicalText(true)));

    LeakHunter.checkLeak(nested, MethodElement.class, node -> node.getPsi().equals(cls.getMethods()[0]));

    ReadAction.runBlocking(() -> {
      GCWatcher.tracking(cls.getNode()).ensureCollected();
      assertFalse(file.isContentsLoaded());

      assertEquals("Comp", nested.getCanonicalText(true));
      assertFalse(file.isContentsLoaded());
    });
  }
}
