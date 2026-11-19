// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil
import com.intellij.codeInsight.daemon.impl.quickfix.CreateServiceClassFixBase
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor
import com.intellij.java.testFramework.fixtures.lightJava9ModulesFixture
import com.intellij.java.testFramework.fixtures.lightJava9ModulesProjectFixture
import com.intellij.openapi.application.EDT
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
class CreateServiceImplementationClassTest {
  companion object {
    private val projectFixture = lightJava9ModulesProjectFixture()
  }

  private val java9 by lightJava9ModulesFixture(projectFixture)
  private val myFixture get() = java9.fixture

  @Test
  fun testExistingPackageAllQualified(): Unit = timeoutRunBlocking {
    java9.addFile("foo/bar/MyService.java", "package foo.bar; public class MyService { }")

    withContext(Dispatchers.EDT) {
      doAction("module foo.bar { provides foo.bar.MyService with foo.bar.<caret>MyServiceImpl; }",
               "foo.bar.MyServiceImpl")
      myFixture.checkResult("foo/bar/MyServiceImpl.java",
                            "package foo.bar;\n\n" +
                            "public class MyServiceImpl extends MyService {\n" +
                            "}\n", true)
    }
  }

  @Test
  fun testExistingPackageInterfaceImported(): Unit = timeoutRunBlocking {
    java9.addFile("foo/bar/MyService.java", "package foo.bar; public class MyService { }")

    withContext(Dispatchers.EDT) {
      doAction("import foo.bar.MyService; module foo.bar { provides MyService with foo.bar.<caret>MyServiceImpl; }",
               "foo.bar.MyServiceImpl")
      myFixture.checkResult("foo/bar/MyServiceImpl.java",
                            "package foo.bar;\n\n" +
                            "public class MyServiceImpl extends MyService {\n" +
                            "}\n", true)
    }
  }

  @Test
  fun testExistingPackageNested(): Unit = timeoutRunBlocking {
    java9.addFile("foo/bar/MyService.java", "package foo.bar; public class MyService { }")
    java9.addFile("foo/bar/Outer.java", "package foo.bar;\n\n" +
                                  "public class Outer {\n" +
                                  "    void aMethod() {}\n" +
                                  "}")

    withContext(Dispatchers.EDT) {
      doAction("module foo.bar { provides foo.bar.MyService with foo.bar.Outer.<caret>MyServiceImpl; }",
               "foo.bar.Outer.MyServiceImpl")
      myFixture.checkResult("foo/bar/Outer.java",
                            "package foo.bar;\n\n" +
                            "public class Outer {\n" +
                            "    void aMethod() {}\n\n" +
                            "    public static class MyServiceImpl extends MyService {\n" +
                            "    }\n" +
                            "}", true)
    }
  }

  @Test
  fun testNonexistentPackage(): Unit = timeoutRunBlocking {
    java9.addFile("foo/bar/MyService.java", "package foo.bar; public class MyService { }")

    withContext(Dispatchers.EDT) {
      doAction("module foo.bar { provides foo.bar.MyService with foo.bar.<caret>baz.MyServiceImpl; }",
               "foo.bar.baz.MyServiceImpl")
      myFixture.checkResult("foo/bar/baz/MyServiceImpl.java",
                            "package foo.bar.baz;\n\n" +
                            "import foo.bar.MyService;\n" +
                            "\n" +
                            "public class MyServiceImpl extends MyService {\n" +
                            "}\n", true)
    }
  }

  @Test
  fun testNestedNonexistentPackage(): Unit = timeoutRunBlocking {
    java9.addFile("foo/bar/MyService.java", "package foo.bar; public class MyService { }")

    withContext(Dispatchers.EDT) {
      doAction("module foo.bar { provides foo.bar.MyService with foo.bar.<caret>baz.boo.MyServiceImpl; }",
               "foo.bar.baz.boo.MyServiceImpl")
      myFixture.checkResult("foo/bar/baz/boo/MyServiceImpl.java",
                            "package foo.bar.baz.boo;\n\n" +
                            "import foo.bar.MyService;\n" +
                            "\n" +
                            "public class MyServiceImpl extends MyService {\n" +
                            "}\n", true)
    }
  }

  @Test
  fun testNestedNonexistentPackageProviderMethod(): Unit = timeoutRunBlocking {
    java9.addFile("foo/bar/MyService.java", "package foo.bar; public class MyService { }")

    withContext(Dispatchers.EDT) {
      doAction("module foo.bar { provides foo.bar.MyService with foo.bar.<caret>baz.boo.MyServiceImpl; }",
               "foo.bar.baz.boo.MyServiceImpl", isSubclass = false)
      myFixture.checkResult("foo/bar/baz/boo/MyServiceImpl.java",
                            "package foo.bar.baz.boo;\n\n" +
                            "import foo.bar.MyService;\n" +
                            "\n" +
                            "public class MyServiceImpl {\n" +
                            "    public static MyService provider() {\n" +
                            "        return null;\n" +
                            "    }\n" +
                            "}\n", true)
    }
  }

  @Test
  fun testMultipleImplementations(): Unit = timeoutRunBlocking {
    java9.addFile("foo/bar/MyService.java", "package foo.bar; public class MyService { }")
    java9.addFile("foo/bar/other/MyServiceOther.java", "package foo.bar.other; import foo.bar.MyService; public class MyServiceOther extends MyService { }")

    withContext(Dispatchers.EDT) {
      doAction("module foo.bar { provides foo.bar.MyService with foo.bar.<caret>MyServiceImpl, foo.bar.other.MyServiceOther; }",
               "foo.bar.MyServiceImpl")
      myFixture.checkResult("foo/bar/MyServiceImpl.java",
                            "package foo.bar;\n\n" +
                            "public class MyServiceImpl extends MyService {\n" +
                            "}\n", true)
    }
  }

  @Test
  fun testAbstractSuperclass(): Unit = timeoutRunBlocking {
    java9.addFile("foo/bar/MyService.java", "package foo.bar; public abstract class MyService { public abstract void doWork(); }")

    withContext(Dispatchers.EDT) {
      doAction("module foo.bar { provides foo.bar.MyService with foo.bar.<caret>MyServiceImpl; }",
               "foo.bar.MyServiceImpl")
      myFixture.checkResult("foo/bar/MyServiceImpl.java",
                            "package foo.bar;\n\n" +
                            "public class MyServiceImpl extends MyService {\n" +
                            "}\n", true)
    }
  }

  @Test
  fun testServiceInterface(): Unit = timeoutRunBlocking {
    java9.addFile("foo/bar/MyService.java", "package foo.bar; public interface MyService { void doWork(); }")

    withContext(Dispatchers.EDT) {
      doAction("module foo.bar { provides foo.bar.MyService with foo.bar.<caret>MyServiceImpl; }",
               "foo.bar.MyServiceImpl")
      myFixture.checkResult("foo/bar/MyServiceImpl.java",
                            "package foo.bar;\n\n" +
                            "public class MyServiceImpl implements MyService {\n" +
                            "}\n", true)
    }
  }

  @Test
  fun testServiceSuperclassFromOtherModule(): Unit = timeoutRunBlocking {
    java9.moduleInfo("module foo.bar.other { exports foo.bar.other; }", OTHER)
    java9.addFile("foo/bar/other/MyService.java", "package foo.bar.other; public class MyService { }", OTHER)

    withContext(Dispatchers.EDT) {
      doAction("module foo.bar.impl { requires foo.bar.other; provides foo.bar.other.MyService with foo.bar.<caret>impl.MyServiceImpl; }",
               "foo.bar.impl.MyServiceImpl")
      myFixture.checkResult("foo/bar/impl/MyServiceImpl.java",
                            "package foo.bar.impl;\n\n" +
                            "import foo.bar.other.MyService;\n" +
                            "\n" +
                            "public class MyServiceImpl extends MyService {\n" +
                            "}\n", true)
    }
  }

  @Test
  fun testServiceSuperclassFromNotReadableModule() {
    java9.moduleInfo("module foo.bar.other { exports foo.bar.other; }", OTHER)
    java9.addFile("foo/bar/other/MyService.java", "package foo.bar.other; public class MyService { }", OTHER)

    doTestNoAction("module foo.bar.impl { provides foo.bar.other.MyService with foo.bar.impl.<caret>MyServiceImpl; }")
  }

  @Test
  fun testExistingLibraryPackage() {
    java9.addFile("foo/bar/MyService.java", "package foo.bar; public class MyService { }")
    doTestNoAction("module foo.bar { provides foo.bar.MyService with java.io.<caret>MyServiceImpl; }")
  }

  @Test
  fun testExistingLibraryOuterClass() {
    java9.addFile("foo/bar/MyService.java", "package foo.bar; public class MyService { }")
    doTestNoAction("module foo.bar { provides foo.bar.MyService with java.io.File.<caret>MyServiceImpl; }")
  }

  private fun doTestNoAction(text: String) {
    myFixture.configureByText("module-info.java", text)
    val filtered = myFixture.availableIntentions.filter { it.text.startsWith("Create class") }
    assertEquals(listOf<IntentionAction>(), filtered)
  }

  private fun doAction(moduleInfoText: String, implementationFQN: String,
                       rootDirectory: PsiDirectory? = null, isSubclass: Boolean = true) {
    val moduleInfo = myFixture.configureByText("module-info.java", moduleInfoText) as PsiJavaFile
    moduleInfo.putUserData(CreateServiceClassFixBase.SERVICE_ROOT_DIR, rootDirectory ?: moduleInfo.containingDirectory)
    moduleInfo.putUserData(CreateServiceClassFixBase.SERVICE_IS_SUBCLASS, isSubclass)

    val action = myFixture.findSingleIntention("Create class '$implementationFQN'")
    myFixture.launchAction(action)
    myFixture.checkHighlighting(false, false, false) // no error
    val serviceImpl = myFixture.findClass(implementationFQN)
    val javaModule = JavaModuleGraphUtil.findDescriptorByElement(serviceImpl)!!
    assertEquals(moduleInfo.moduleDeclaration, javaModule)
  }

  private val OTHER = MultiModuleJava9ProjectDescriptor.ModuleDescriptor.M2
}