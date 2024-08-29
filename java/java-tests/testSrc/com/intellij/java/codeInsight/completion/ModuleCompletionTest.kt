// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.*
import com.intellij.testFramework.NeedsIndex
import org.assertj.core.api.Assertions.assertThat
import java.util.jar.JarFile

class ModuleCompletionTest : LightJava9ModulesCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    addFile("pkg/empty/package-info.java", "package pkg.empty;")
    addFile("pkg/main/MyAnno.java", "package pkg.main;\npublic @interface MyAnno { }")
    addFile("pkg/main/MySvc.java", "package pkg.main;\npublic class MySvc { }")
    addFile("pkg/other/MySvcImpl.groovy", "package pkg.other\nclass MySvcImpl extends pkg.main.MySvc { }")
    addFile("module-info.java", "module M2 { }", M2)
    addFile(JarFile.MANIFEST_NAME, "Manifest-Version: 1.0\nAutomatic-Module-Name: all.fours\n", M4)
  }

  fun testFileHeader() = variants("<caret>", "module", "open")
  fun testFileHeaderAfterComment() = variants("/** a comment */\n<caret>", "module", "open")
  fun testFileHeaderAfterImport() = variants("import java.lang.Deprecated;\n<caret>", "module", "open")
  fun testFileHeaderAfterAnnotation() = variants("@Deprecated <caret>", "module", "open")
  fun testFileHeaderAfterOpen() = complete("open <caret>", "open module <caret>")
  fun testFileHeaderAfterModule() = variants("module M { }\n<caret>")
  fun testAnnotation() = complete("@Dep<caret>", "@Deprecated<caret>")
  fun testAnnotationBeforeModule() = complete("@Dep<caret> module M { }", "@Deprecated<caret> module M { }")
  fun testNoCompletionInModuleName() = variants("module M<caret>")
  fun testNoStatementsInModuleName() = variants("module test.module.<caret> { }")

  fun testStatementsInEmptyModule() = variants("module M { <caret> }", "requires", "exports", "opens", "uses", "provides")
  fun testStatementsAfterComment() = variants("module M { /*requires X;*/ <caret> }", "requires", "exports", "opens", "uses", "provides")
  fun testStatementsAfterStatement() = variants("module M { requires X; <caret> }", "requires", "exports", "opens", "uses", "provides")
  fun testStatementsUnambiguous() = complete("module M { requires X; ex<caret> }", "module M { requires X; exports <caret> }")

  @NeedsIndex.Full
  fun testRequiresBare() =
    variants("module M { requires <caret>",
             "transitive", "static", "M2", "java.base", "java.non.root", "java.se", "java.xml.bind", "java.xml.ws",
             "lib.multi.release", "lib.named", "lib.auto", "lib.claimed", "all.fours", "lib.with.module.info")

  fun testRequiresTransitive() = complete("module M { requires tr<caret> }", "module M { requires transitive <caret> }")

  @NeedsIndex.Full
  fun testRequiresSimpleName() = complete("module M { requires M<caret> }", "module M { requires M2;<caret> }")

  @NeedsIndex.ForStandardLibrary
  fun testRequiresQualifiedName() = complete("module M { requires lib.mult<caret> }", "module M { requires lib.multi.release;<caret> }")

  @NeedsIndex.ForStandardLibrary
  fun testRequiresWithNextLine() {
    myFixture.configureByText("module-info.java", "module M { requires lib.mult<caret>\nrequires java.io;}")
    myFixture.completeBasic()
    myFixture.type('\t')
    myFixture.checkResult("module M { requires lib.multi.release;  \nrequires java.io;}")
  }

  fun testExportsBare() = variants("module M { exports <caret> }", "pkg")
  fun testExportsPrefixed() = complete("module M { exports p<caret> }", "module M { exports pkg<caret> }")
  fun testExportsQualified() = variants("module M { exports pkg.<caret> }", "main", "other", "empty")
  fun testExportsQualifiedUnambiguous() = complete("module M { exports pkg.o<caret> }", "module M { exports pkg.other<caret> }")
  fun testExportsQualifiedUnambiguousDot() {
    myFixture.configureByText("module-info.java", "module M { exports pkg.o<caret> }")
    myFixture.complete(CompletionType.BASIC, 0)
    myFixture.type('.')
    myFixture.checkResult("module M { exports pkg.other.<caret> }")
  }

  fun testExportsTo() = complete("module M { exports pkg.other <caret> }", "module M { exports pkg.other to <caret> }")

  @NeedsIndex.Full
  fun testExportsToList() =
    variants("module M { exports pkg.other to <caret> }",
             "M2", "java.base", "java.non.root", "java.se", "java.xml.bind", "java.xml.ws", "lib.multi.release", "lib.named",
             "lib.with.module.info")

  @NeedsIndex.Full
  fun testExportsToUnambiguous() = complete("module M { exports pkg.other to M<caret> }", "module M { exports pkg.other to M2<caret> }")

  fun testUsesPrefixed() = complete("module M { uses p<caret> }", "module M { uses pkg.<caret> }")
  // TODO return it after refactoring the completion algorithm.
  //fun testUsesQualified1() = variants("module M { uses pkg.<caret> }", "main", "other", "empty")
  fun testUsesQualified2() = variants("module M { uses pkg.main.<caret> }", "MyAnno", "MySvc")
  fun testUsesUnambiguous() = complete("module M { uses pkg.main.MS<caret> }", "module M { uses pkg.main.MySvc<caret> }")

  fun testProvidesPrefixed() = complete("module M { provides p<caret> }", "module M { provides pkg.<caret> }")
  // TODO return it after refactoring the completion algorithm.
  //fun testProvidesQualified1() = variants("module M { provides pkg.<caret> }", "main", "other", "empty")
  fun testProvidesQualified2() = variants("module M { provides pkg.main.<caret> }", "MyAnno", "MySvc")
  fun testProvidesUnambiguous() = complete("module M { provides pkg.main.MS<caret> }", "module M { provides pkg.main.MySvc<caret> }")
  fun testProvidesWith() = complete("module M { provides pkg.main.MySvc <caret> }", "module M { provides pkg.main.MySvc with <caret> }")
  fun testProvidesWithPrefixed() =
    complete("module M { provides pkg.main.MySvc with p<caret> }", "module M { provides pkg.main.MySvc with pkg.<caret> }")

  fun testProvidesWithQualified() =
    complete("module M { provides pkg.main.MySvc with pkg.o<caret> }", "module M { provides pkg.main.MySvc with pkg.other.<caret> }")

  fun testProvidesWithUnambiguous() =
    complete("module M { provides pkg.main.MySvc with pkg.other.M<caret> }",
             "module M { provides pkg.main.MySvc with pkg.other.MySvcImpl<caret> }")

  @NeedsIndex.SmartMode(reason = "Smart mode is necessary for optimizing imports; full index is needed for inheritance check")
  fun testProvidesOrder() {
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
  fun testImports() {
    addFile("module-info.java", "module M { requires M2; }")
    addFile("module-info.java", "module M2 { exports pkg.m2; }", M2)
    addFile("pkg/m2/C2.java", "package pkg.m2;\npublic class C2 { }", M2)
    addFile("pkg/m2/impl/C2Impl.java", "package pkg.m2.impl;\npublic class C2Impl { }", M2)
    myFixture.configureByText("test.java", "import pkg.m2.<caret>")
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).containsExactly("*", "C2")  // no 'C2Impl'
  }

  fun testTypeParameter() {
    addFile("module-info.java", "module M { }")
    addTestFile("whatever/test.txt", "-")
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