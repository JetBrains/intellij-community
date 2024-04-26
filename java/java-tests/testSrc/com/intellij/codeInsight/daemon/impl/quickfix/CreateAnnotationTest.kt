// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix


import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.jvm.actions.*
import com.intellij.psi.PsiJvmModifiersOwner
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class CreateAnnotationTest : LightJavaCodeInsightFixtureTestCase() {

  private fun createAnnotationAction(modifierListOwner: PsiJvmModifiersOwner, annotationRequest: AnnotationRequest): IntentionAction =
    createAddAnnotationActions(modifierListOwner, annotationRequest).single()

  fun `test add annotation with value text literal`() {
    myFixture.configureByText("A.java", """
      class A {
      void bar(){}
      }
    """.trimIndent())

    val modifierListOwner = myFixture.findElementByText("bar", PsiJvmModifiersOwner::class.java)

    myFixture.launchAction(createAnnotationAction(modifierListOwner,
                                                  annotationRequest("java.lang.SuppressWarnings",
                                                                    stringAttribute("value", "someText"))))
    myFixture.checkResult("""
      class A {
          @SuppressWarnings("someText")
          void bar(){}
      }
    """.trimIndent())
  }

  fun `test add annotation with two parameters`() {
    myFixture.addClass("""
      public @interface Anno{

      String text();

      int num();

      }
    """.trimIndent())
    myFixture.configureByText("A.java", """
      class A {}
    """.trimIndent())

    val modifierListOwner = myFixture.findElementByText("A", PsiJvmModifiersOwner::class.java)

    myFixture.launchAction(createAnnotationAction(modifierListOwner,
                                                  annotationRequest(
                                                    "java.lang.SuppressWarnings",
                                                    intAttribute("num", 12),
                                                    stringAttribute("text", "anotherText")
                                                  )
    )
    )
    myFixture.checkResult("""
      @SuppressWarnings(num = 12, text = "anotherText")
      class A {}
    """.trimIndent())
  }

  fun `test add annotation with class access literal`() {
    myFixture.addClass("""
      public @interface Anno {
        Class<?> value();
      }
    """.trimIndent())
    myFixture.configureByText("A.java", """
      class A {}
    """.trimIndent())

    val modifierListOwner = myFixture.findElementByText("A", PsiJvmModifiersOwner::class.java)

    myFixture.launchAction(createAnnotationAction(modifierListOwner,
                                                  annotationRequest(
                                                    "Anno",
                                                    classAttribute("value", "A"),
                                                  )
    )
    )
    myFixture.checkResult("""
      @Anno(A.class)
      class A {}
    """.trimIndent())
  }

  fun `test add annotation with field reference`() {
    myFixture.addClass("""
      public @interface Anno {
        int field1();
        int field2();
        int field3();
      }
    """.trimIndent())
    myFixture.configureByText("A.java", """
      class A {
        private static final int CONSTANT = 1;
      }
    """.trimIndent())

    val modifierListOwner = myFixture.findElementByText("A", PsiJvmModifiersOwner::class.java)

    val annotation = annotationRequest("Anno",
                                       constantAttribute("field1", "A.CONSTANT"),
                                       constantAttribute("field2", "A.INVALID_CONSTANT"),
                                       constantAttribute("field3", "CONSTANT"))
    myFixture.launchAction(createAnnotationAction(modifierListOwner, annotation))
    myFixture.checkResult("""
      @Anno(field1 = A.CONSTANT, field2 = A.INVALID_CONSTANT, field3 = CONSTANT)
      class A {
        private static final int CONSTANT = 1;
      }
    """.trimIndent())
  }

  fun `test add annotation with array value`() {
    myFixture.addClass("""
      public @interface Anno {
        String[] value();
      }
    """.trimIndent())
    myFixture.configureByText("A.java", """
      class A {}
    """.trimIndent())

    val modifierListOwner = myFixture.findElementByText("A", PsiJvmModifiersOwner::class.java)

    val arrayMembers = listOf(
      AnnotationAttributeValueRequest.StringValue("member 1"),
      AnnotationAttributeValueRequest.StringValue("member 2"),
      AnnotationAttributeValueRequest.StringValue("member 3")
    )
    val annotation = annotationRequest("Anno", arrayAttribute("value", arrayMembers))
    myFixture.launchAction(createAnnotationAction(modifierListOwner, annotation))
    myFixture.checkResult("""
      @Anno({"member 1", "member 2", "member 3"})
      class A {}
    """.trimIndent())
  }

  fun `test add annotation with deep nesting`() {
    myFixture.addClass("""
      public @interface Root {
        Nested1 value();
      }
    """.trimIndent())
    myFixture.addClass("""
      public @interface Nested1 {
        Nested2[] value() default {};
      }
    """.trimIndent())
    myFixture.addClass("""
      public @interface Nested2 {
        Nested1 single();
        Nested3[] array() default {};
      }
    """.trimIndent())
    myFixture.addClass("""
      public @interface Nested3 {}
    """.trimIndent())
    myFixture.configureByText("A.java", """
      class A {}
    """.trimIndent())

    val modifierListOwner = myFixture.findElementByText("A", PsiJvmModifiersOwner::class.java)

    val nested2_1 = annotationRequest(
      "Nested2",
      nestedAttribute("single", annotationRequest("Nested1")),
      arrayAttribute("array", listOf(
        AnnotationAttributeValueRequest.NestedAnnotation(annotationRequest("Nested3")),
        AnnotationAttributeValueRequest.NestedAnnotation(annotationRequest("Nested3"))
      ))
    )
    val nested2_2 = annotationRequest(
      "Nested2",
      nestedAttribute("single", annotationRequest("Nested1", arrayAttribute("value", listOf(
        AnnotationAttributeValueRequest.NestedAnnotation(
          annotationRequest("Nested2", nestedAttribute("single", annotationRequest("Nested1"))))
      ))))
    )
    val nested1 = annotationRequest("Nested1", arrayAttribute("value", listOf(
      AnnotationAttributeValueRequest.NestedAnnotation(nested2_1),
      AnnotationAttributeValueRequest.NestedAnnotation(nested2_2)
    )))
    val root = annotationRequest("Root", nestedAttribute("value", nested1))
    myFixture.launchAction(createAnnotationAction(modifierListOwner, root))
    myFixture.checkResult("""
      @Root(@Nested1({@Nested2(single = @Nested1, array = {@Nested3, @Nested3}), @Nested2(single = @Nested1({@Nested2(single = @Nested1)}))}))
      class A {}
    """.trimIndent())
  }

}
