/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.find.impl

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiNameValuePair
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

class JavaReadWriteAccessTest : LightCodeInsightFixtureTestCase() {
  fun testWriteAnnotationsMethodExplicitCall() {
    val anno = myFixture.addClass("@interface Anno {String value() default \"\";}")
    val detector = ReadWriteAccessDetector.findDetector(anno.methods[0])
    assertNotNull(detector)

    val file: PsiFile = myFixture.configureByText("A.java", "@Anno(val<caret>ue = \"foo\") class A {}")
    val element = file.findElementAt(myFixture.editor.caretModel.offset)
    assertNotNull(element)
    val expressionAccess = detector!!.getExpressionAccess(element!!)
    assertEquals(ReadWriteAccessDetector.Access.Write, expressionAccess)
  }

  fun testWriteAnnotationsMethodImplicitCall() {
    val anno = myFixture.addClass("@interface Anno {String value() default \"\";}")
    val detector = ReadWriteAccessDetector.findDetector(anno.methods[0])
    assertNotNull(detector)

    val file: PsiFile = myFixture.configureByText("A.java", "@Anno(\"fo<caret>o\") class A {}")
    val element = file.findElementAt(myFixture.editor.caretModel.offset)
    assertNotNull(element)
    val expressionAccess = detector!!.getExpressionAccess(PsiTreeUtil.getParentOfType(element, PsiNameValuePair::class.java, true)!!)
    assertEquals(ReadWriteAccessDetector.Access.Write, expressionAccess)
  }

  fun testExplicitCallToAnnotationMethod() {
    val anno = myFixture.addClass("@interface Anno {String value() default \"\";}")
    val detector = ReadWriteAccessDetector.findDetector(anno.methods[0])
    assertNotNull(detector)

    val file: PsiFile = myFixture.configureByText("A.java", "import java.lang.reflect.Field;" +
                                                            "class A { " +
                                                            " void m(Field field) { " +
                                                            "   Anno a = field.getAnnotation(Anno.class);" +
                                                            "   System.out.println(a.val<caret>ue());" +
                                                            " }" +
                                                            "}")
    val element = file.findElementAt(myFixture.editor.caretModel.offset)
    assertNotNull(element)
    val expressionAccess = detector!!.getExpressionAccess(PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression::class.java, true)!!)
    assertEquals(ReadWriteAccessDetector.Access.Read, expressionAccess)
  }
}