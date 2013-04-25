/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightIdeaTestCase

@SuppressWarnings(["GrUnresolvedAccess", "GroovyAssignabilityCheck"])
class AnnotatedTypeTest extends LightIdeaTestCase {

  public void testTypeComposition() {
    PsiFile context = createFile("typeCompositionTest.java", """
import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;

@interface A { }
@Target({TYPE_USE}) @interface TA { int value() default 42; }

class E1 extends Exception { }
class E2 extends Exception { }
""")
    PsiElement psi

    psi = javaFacade.elementFactory.createStatementFromText("@A @TA(1) int @TA(2) [] a", context)
    assertEquals("@TA(1) int @TA(2) []", psi.declaredElements[0].type.presentableText)

    psi = javaFacade.elementFactory.createStatementFromText("try { } catch (@A @TA(1) E1 | @TA(2) E2 e) { }", context)
    assertEquals("@TA(1) E1 | @TA(2) E2", psi.catchBlockParameters[0].type.presentableText)

    psi = javaFacade.elementFactory.createStatementFromText("@A @TA(1) String @TA(2) [] f @TA(3) []", context)
    assertEquals("@TA(1) String @TA(2) [] @TA(3) []", psi.declaredElements[0].type.presentableText)

    psi = javaFacade.elementFactory.createStatementFromText("Class<@TA(1) ?> c", context)
    assertEquals("Class<@TA(1) ?>", psi.declaredElements[0].type.presentableText)

    psi = javaFacade.elementFactory.createStatementFromText("Class<@TA String> cs = new Class<>()", context)
    assertEquals("Class<@TA String>", psi.declaredElements[0].initializer.type.presentableText)

    psi = javaFacade.elementFactory.createStatementFromText("@A @TA(1) String s", context)
    assertEquals("@TA(1) String", psi.declaredElements[0].type.presentableText)

    psi = javaFacade.elementFactory.createStatementFromText("@A java.lang.@TA(1) String s", context)
    assertEquals("@TA(1) String", psi.declaredElements[0].type.presentableText)

    psi = javaFacade.elementFactory.createStatementFromText("Collection<? extends> s", context)
    assertEquals("Collection<?>", psi.declaredElements[0].type.presentableText)
  }

}
