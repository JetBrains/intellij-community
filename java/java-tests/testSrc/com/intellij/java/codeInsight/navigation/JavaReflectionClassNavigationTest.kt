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

import com.intellij.psi.PsiClass
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

/**
 * @author Pavel.Dolgov
 */
class JavaReflectionClassNavigationTest : JavaReflectionClassNavigationTestBase() {

  fun testPublicClass() {
    myFixture.addClass("package foo.bar; public class PublicClass {}")
    doTest("foo.bar.PublicClass")
  }

  fun testPublicInnerClass() {
    myFixture.addClass("package foo.bar; public class PublicClass { public class PublicInnerClass {} }")
    doTest("foo.bar.PublicClass\$PublicInnerClass")
  }

  fun testPublicInnerClass2() {
    myFixture.addClass("package foo.bar; public class PublicClass { public class PublicInnerClass {} }")
    doTest("foo.bar.<caret>PublicClass\$PublicInnerClass")
  }

  fun testPackageLocalClass() {
    myFixture.addClass("package foo.bar; class PackageLocalClass {}")
    doTest("foo.bar.PackageLocalClass")
  }

  fun testPrivateInnerClass() {
    myFixture.addClass("package foo.bar; public class PublicClass { private class PrivateInnerClass {} }")
    doTest("foo.bar.PublicClass\$PrivateInnerClass")
  }

  fun testPrivateInnerClass2() {
    myFixture.addClass("package foo.bar; public class PublicClass { private class PrivateInnerClass {} }")
    doTest("foo.bar.<caret>PublicClass\$PrivateInnerClass")
  }

  fun testWithClassLoader() {
    myFixture.addClass("package foo.bar; public class PublicClass {}")
    doTest("foo.bar.<caret>PublicClass\$PrivateInnerClass", { "Thread.currentThread().getContextClassLoader().loadClass(\"$it\")" })
  }

  fun testNestedClassDefaultPackage() {
    myFixture.addClass("public class Outer { public static class Inner {} }")
    doTest("Outer\$Inner")
  }
}

class Java9ReflectionClassNavigationTest : JavaReflectionClassNavigationTestBase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = JAVA_9

  fun testJava9MethodHandlesLookup() {
    myFixture.addClass("package foo.bar; public class PublicClass {}")
    doTest("foo.bar.PublicClass", { "java.lang.invoke.MethodHandles.lookup().findClass(\"$it\")" })
  }
}

abstract class JavaReflectionClassNavigationTestBase : LightCodeInsightFixtureTestCase() {
  protected fun doTest(className: String, usageFormatter: (String) -> String = { "Class.forName(\"$it\")" }) {
    val caretPos = className.indexOf("<caret>")
    val atCaret: String
    var expectedName = className.replace('$', '.')
    if (caretPos >= 0) {
      atCaret = className
      val dotPos = expectedName.indexOf(".", caretPos + 1)
      if (dotPos >= 0) expectedName = expectedName.substring(0, dotPos).replace("<caret>", "")
    }
    else {
      atCaret = className + "<caret>"
    }
    myFixture.configureByText("Main.java",
                              """import foo.bar.*;
                              class Main {
                                void foo() throws ReflectiveOperationException {
                                  ${usageFormatter(atCaret)};
                                }
                              }""")
    val reference = myFixture.file.findReferenceAt(myFixture.caretOffset)
    assertNotNull("No reference at the caret", reference)
    val resolved = reference!!.resolve()
    assertTrue("Class not resolved: " + reference.canonicalText, resolved is PsiClass)
    val psiClass = resolved as? PsiClass
    assertEquals("Class name", expectedName, psiClass?.qualifiedName)
  }
}
