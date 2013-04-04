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

import com.intellij.psi.*
import com.intellij.testFramework.LightIdeaTestCase

class AnnotatedTypeTest extends LightIdeaTestCase {
  @SuppressWarnings("GrUnresolvedAccess")
  public void testTypeComposition() {
    def context = createFile("typeCompositionTest.java", """
import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;

@interface A { }
@Target({TYPE_USE}) @interface TA { }

class E1 extends Exception { }
class E2 extends Exception { }
""")
    def factory = JavaPsiFacade.getInstance(getProject()).getElementFactory(), psi

    psi = factory.createStatementFromText("@TA int @TA [] a = null", context)
    assertEquals("@TA int @TA []", psi.declaredElements[0].type.presentableText)

    psi = factory.createStatementFromText("@A int @TA [] a = null", context)
    assertEquals("int @TA []", psi.declaredElements[0].type.presentableText)

    psi = factory.createStatementFromText("try { } catch (@TA E1 | @TA E2 e) { }", context)
    assertEquals("@TA E1 | @TA E2", psi.catchBlockParameters[0].type.presentableText)

    psi = factory.createFieldFromText("@TA String @TA [] f @TA []", context)
    assertEquals("@TA String @TA [] @TA []", psi.type.presentableText)
  }
}
