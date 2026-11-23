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
package com.intellij.java.codeInsight.navigation

import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiClass
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class JavaReflectionClassNavigationTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun testPublicClass(): Unit = timeoutRunBlocking {
    myFixture.addClass("package foo.bar; public class PublicClass {}")
    doTest(myFixture, "foo.bar.PublicClass")
  }

  @Test
  fun testPublicInnerClass(): Unit = timeoutRunBlocking {
    myFixture.addClass("package foo.bar; public class PublicClass { public class PublicInnerClass {} }")
    doTest(myFixture, "foo.bar.PublicClass\$PublicInnerClass")
  }

  @Test
  fun testPublicInnerClass2(): Unit = timeoutRunBlocking {
    myFixture.addClass("package foo.bar; public class PublicClass { public class PublicInnerClass {} }")
    doTest(myFixture, "foo.bar.<caret>PublicClass\$PublicInnerClass")
  }

  @Test
  fun testPackageLocalClass(): Unit = timeoutRunBlocking {
    myFixture.addClass("package foo.bar; class PackageLocalClass {}")
    doTest(myFixture, "foo.bar.PackageLocalClass")
  }

  @Test
  fun testPrivateInnerClass(): Unit = timeoutRunBlocking {
    myFixture.addClass("package foo.bar; public class PublicClass { private class PrivateInnerClass {} }")
    doTest(myFixture, "foo.bar.PublicClass\$PrivateInnerClass")
  }

  @Test
  fun testPrivateInnerClass2(): Unit = timeoutRunBlocking {
    myFixture.addClass("package foo.bar; public class PublicClass { private class PrivateInnerClass {} }")
    doTest(myFixture, "foo.bar.<caret>PublicClass\$PrivateInnerClass")
  }

  @Test
  fun testWithClassLoader(): Unit = timeoutRunBlocking {
    myFixture.addClass("package foo.bar; public class PublicClass {}")
    doTest(myFixture, "foo.bar.<caret>PublicClass\$PrivateInnerClass", { "Thread.currentThread().getContextClassLoader().loadClass(\"$it\")" })
  }

  @Test
  fun testNestedClassDefaultPackage(): Unit = timeoutRunBlocking {
    myFixture.addClass("public class Outer { public static class Inner {} }")
    doTest(myFixture, "Outer\$Inner")
  }
}

@TestApplication
class Java9ReflectionClassNavigationTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_9)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun testJava9MethodHandlesLookup(): Unit = timeoutRunBlocking {
    myFixture.addClass("package foo.bar; public class PublicClass {}")
    doTest(myFixture, "foo.bar.PublicClass", { "java.lang.invoke.MethodHandles.lookup().findClass(\"$it\")" })
  }
}

private suspend fun doTest(myFixture: LightJavaCodeInsightTestFixture, className: String, usageFormatter: (String) -> String = { "Class.forName(\"$it\")" }) {
  val caretPos = className.indexOf("<caret>")
  val atCaret: String
  var expectedName = className.replace('$', '.')
  if (caretPos >= 0) {
    atCaret = className
    val dotPos = expectedName.indexOf(".", caretPos + 1)
    if (dotPos >= 0) expectedName = expectedName.substring(0, dotPos).replace("<caret>", "")
  }
  else {
    atCaret = "$className<caret>"
  }
  myFixture.configureByText("Main.java",
                            """import foo.bar.*;
                              class Main {
                                void foo() throws ReflectiveOperationException {
                                  ${usageFormatter(atCaret)};
                                }
                              }""")
  readAction {
    val reference = myFixture.file.findReferenceAt(myFixture.caretOffset)
    assertNotNull(reference, "No reference at the caret")
    val resolved = reference!!.resolve()
    assertTrue(resolved is PsiClass, "Class not resolved: " + reference.canonicalText)
    val psiClass = resolved as? PsiClass
    assertEquals(expectedName, psiClass?.qualifiedName, "Class name")
  }
}
