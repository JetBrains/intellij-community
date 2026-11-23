// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.M2
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.M4
import com.intellij.java.testFramework.fixtures.lightJava9ModulesFixture
import com.intellij.java.testFramework.fixtures.lightJava9ModulesProjectFixture
import com.intellij.openapi.application.EDT
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate
import com.intellij.testFramework.NeedsIndex
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.jar.JarFile

@TestApplication
@IndexingModeTemplate
class ModuleCompletionTest {
  companion object {
    private val projectFixture = lightJava9ModulesProjectFixture()
  }

  private val java9 by lightJava9ModulesFixture(projectFixture)
  private val myFixture get() = java9.fixture

  @BeforeEach
  fun setUp(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    java9.addFile("pkg/empty/package-info.java", "package pkg.empty;")
    java9.addFile("pkg/main/MyAnno.java", "package pkg.main;\npublic @interface MyAnno { }")
    java9.addFile("pkg/main/MySvc.java", "package pkg.main;\npublic class MySvc { }")
    java9.addFile("pkg/other/MySvcImpl.groovy", "package pkg.other\nclass MySvcImpl extends pkg.main.MySvc { }")
    java9.addFile("module-info.java", "module M2 { }", M2)
    java9.addFile(JarFile.MANIFEST_NAME, "Manifest-Version: 1.0\nAutomatic-Module-Name: all.fours\n", M4)
  }

  @Test
  fun testFileHeader(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) { variants("<caret>", "import", "module", "open") }
  @Test
  fun testFileHeaderAfterComment(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    variants("/** a comment */\n<caret>", "import", "module", "open")
  }
  @Test
  fun testFileHeaderAfterImport(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    variants("import java.lang.Deprecated;\n<caret>", "import", "module", "open")
  }
  @Test
  fun testFileHeaderAfterAnnotation(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    variants("@Deprecated <caret>", "module", "open")
  }
  @Test
  fun testFileHeaderAfterOpen(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) { complete("open <caret>", "open module <caret>") }
  @Test
  fun testFileHeaderAfterModule(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) { variants("module M { }\n<caret>") }
  @Test
  fun testAnnotation(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) { complete("@Dep<caret>", "@Deprecated<caret>") }
  @Test
  fun testAnnotationBeforeModule(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    complete("@Dep<caret> module M { }", "@Deprecated<caret> module M { }")
  }
  @Test
  fun testNoCompletionInModuleName(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) { variants("module M<caret>") }
  @Test
  fun testNoStatementsInModuleName(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) { variants("module test.module.<caret> { }") }

  @Test
  fun testStatementsInEmptyModule(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    variants("module M { <caret> }", "requires", "exports", "opens", "uses", "provides")
  }
  @Test
  fun testStatementsAfterComment(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    variants("module M { /*requires X;*/ <caret> }", "requires", "exports", "opens", "uses", "provides")
  }
  @Test
  fun testStatementsAfterStatement(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    variants("module M { requires X; <caret> }", "requires", "exports", "opens", "uses", "provides")
  }
  @Test
  fun testStatementsUnambiguous(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    complete("module M { requires X; ex<caret> }", "module M { requires X; exports <caret> }")
  }

  @NeedsIndex.Full
  @Test
  fun testRequiresBare(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    variants("module M { requires <caret>",
             "transitive", "static", "M2", "java.base", "java.non.root", "java.se", "java.xml.bind", "java.xml.ws",
             "lib.multi.release", "lib.named", "lib.auto", "lib.claimed", "all.fours", "lib.with.module.info")
  }

  @Test
  fun testRequiresTransitive(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    complete("module M { requires tr<caret> }", "module M { requires transitive <caret> }")
  }

  @NeedsIndex.Full
  @Test
  fun testRequiresSimpleName(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    complete("module M { requires M<caret> }", "module M { requires M2;<caret> }")
  }

  @NeedsIndex.ForStandardLibrary
  @Test
  fun testRequiresQualifiedName(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    complete("module M { requires lib.mult<caret> }", "module M { requires lib.multi.release;<caret> }")
  }

  @NeedsIndex.ForStandardLibrary
  @Test
  fun testRequiresWithNextLine(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    myFixture.configureByText("module-info.java", "module M { requires lib.mult<caret>\nrequires java.io;}")
    myFixture.completeBasic()
    myFixture.type('\t')
    myFixture.checkResult("module M { requires lib.multi.release;  \nrequires java.io;}")
  }

  @Test
  fun testExportsBare(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) { variants("module M { exports <caret> }", "pkg") }
  @Test
  fun testExportsPrefixed(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    complete("module M { exports p<caret> }", "module M { exports pkg<caret> }")
  }
  @Test
  fun testExportsQualified(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    variants("module M { exports pkg.<caret> }", "main", "other", "empty")
  }
  @Test
  fun testExportsQualifiedUnambiguous(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    complete("module M { exports pkg.o<caret> }", "module M { exports pkg.other<caret> }")
  }
  @Test
  fun testExportsQualifiedUnambiguousDot(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    myFixture.configureByText("module-info.java", "module M { exports pkg.o<caret> }")
    myFixture.complete(CompletionType.BASIC, 0)
    myFixture.type('.')
    myFixture.checkResult("module M { exports pkg.other.<caret> }")
  }

  @Test
  fun testExportsTo(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    complete("module M { exports pkg.other <caret> }", "module M { exports pkg.other to <caret> }")
  }

  @NeedsIndex.Full
  @Test
  fun testExportsToList(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    variants("module M { exports pkg.other to <caret> }",
             "M2", "java.base", "java.non.root", "java.se", "java.xml.bind", "java.xml.ws", "lib.multi.release", "lib.named",
             "lib.with.module.info")
  }

  @NeedsIndex.Full
  @Test
  fun testExportsToUnambiguous(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    complete("module M { exports pkg.other to M<caret> }", "module M { exports pkg.other to M2<caret> }")
  }

  @Test
  fun testUsesPrefixed(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    complete("module M { uses p<caret> }", "module M { uses pkg.<caret> }")
  }
  // TODO return it after refactoring the completion algorithm.
  //fun testUsesQualified1() = variants("module M { uses pkg.<caret> }", "main", "other", "empty")
  @Test
  fun testUsesQualified2(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    variants("module M { uses pkg.main.<caret> }", "MyAnno", "MySvc")
  }
  @Test
  fun testUsesUnambiguous(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    complete("module M { uses pkg.main.MS<caret> }", "module M { uses pkg.main.MySvc<caret> }")
  }

  @Test
  fun testProvidesPrefixed(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    complete("module M { provides p<caret> }", "module M { provides pkg.<caret> }")
  }
  // TODO return it after refactoring the completion algorithm.
  //fun testProvidesQualified1() = variants("module M { provides pkg.<caret> }", "main", "other", "empty")
  @Test
  fun testProvidesQualified2(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    variants("module M { provides pkg.main.<caret> }", "MyAnno", "MySvc")
  }
  @Test
  fun testProvidesUnambiguous(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    complete("module M { provides pkg.main.MS<caret> }", "module M { provides pkg.main.MySvc<caret> }")
  }
  @Test
  fun testProvidesWith(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    complete("module M { provides pkg.main.MySvc <caret> }", "module M { provides pkg.main.MySvc with <caret> }")
  }
  @Test
  fun testProvidesWithPrefixed(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    complete("module M { provides pkg.main.MySvc with p<caret> }", "module M { provides pkg.main.MySvc with pkg.<caret> }")
  }

  @Test
  fun testProvidesWithQualified(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    complete("module M { provides pkg.main.MySvc with pkg.o<caret> }", "module M { provides pkg.main.MySvc with pkg.other.<caret> }")
  }

  @Test
  fun testProvidesWithUnambiguous(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    complete("module M { provides pkg.main.MySvc with pkg.other.M<caret> }",
             "module M { provides pkg.main.MySvc with pkg.other.MySvcImpl<caret> }")
  }

  @NeedsIndex.SmartMode(reason = "Smart mode is necessary for optimizing imports; full index is needed for inheritance check")
  @Test
  fun testProvidesOrder(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    myFixture.configureByText("module-info.java", "import pkg.main.*; module M {provides MySvc with M<caret>}")
    myFixture.completeBasic()
    assertEquals(listOf("MySvc", "MySvcImpl", "MyAnno"), myFixture.lookupElementStrings)
    myFixture.lookup.currentItem = myFixture.lookupElements!![1]
    myFixture.type('\n')
    myFixture.checkResult("import pkg.main.*;\n" +
                          "import pkg.other.MySvcImpl;\n" +
                          "\n" +
                          "module M {provides MySvc with MySvcImpl\n" +
                          "}")
  }

  @NeedsIndex.Full
  @Test
  fun testImports(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    java9.addFile("module-info.java", "module M { requires M2; }")
    java9.addFile("module-info.java", "module M2 { exports pkg.m2; }", M2)
    java9.addFile("pkg/m2/C2.java", "package pkg.m2;\npublic class C2 { }", M2)
    java9.addFile("pkg/m2/impl/C2Impl.java", "package pkg.m2.impl;\npublic class C2Impl { }", M2)
    myFixture.configureByText("test.java", "import pkg.m2.<caret>")
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).containsExactly("*", "C2")  // no 'C2Impl'
  }

  @Test
  fun testTypeParameter(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    java9.addFile("module-info.java", "module M { }")
    java9.addTestFile("whatever/test.txt", "-")
    myFixture.configureByText("test.java", "package whatever;\nclass Foo<TParam> { TPar<caret> p; }")
    myFixture.completeBasic()
    myFixture.checkResult("package whatever;\nclass Foo<TParam> { TParam<caret> p; }")
  }


  //<editor-fold desc="Helpers.">
  private fun complete(text: String, expected: String) {
    myFixture.configureByText("module-info.java", text)
    myFixture.completeBasic()
    myFixture.checkResult(expected)
  }

  private fun variants(text: String, vararg variants: String) = fileVariants("module-info.java", text, *variants)
  private fun fileVariants(fileName: String, text: String, vararg variants: String) {
    myFixture.configureByText(fileName, text)
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).containsExactlyInAnyOrder(*variants)
  }
  //</editor-fold>
}