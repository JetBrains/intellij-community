// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.completion

import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.M2
import org.assertj.core.api.Assertions.assertThat

class ModuleCompletionTest : LightJava9ModulesCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    addFile("pkg/empty/package-info.java", "package pkg.empty;")
    addFile("pkg/main/MyAnno.java", "package pkg.main;\npublic @interface MyAnno { }")
    addFile("pkg/main/MySvc.java", "package pkg.main;\npublic class MySvc { }")
    addFile("pkg/other/MySvcImpl.groovy", "package pkg.other\nclass MySvcImpl extends pkg.main.MySvc { }")
    addFile("module-info.java", "module M2 { }", M2)
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

  fun testRequiresBare() =
    variants("module M { requires <caret>", "transitive", "static", "M2", "java.base", "lib.multi.release", "lib.named", "lib.auto", "lib.claimed")
  fun testRequiresTransitive() = complete("module M { requires tr<caret> }", "module M { requires transitive <caret> }")
  fun testRequiresSimpleName() = complete("module M { requires M<caret> }", "module M { requires M2;<caret> }")
  fun testRequiresQualifiedName() = complete("module M { requires lib.m<caret> }", "module M { requires lib.multi.release;<caret> }")

  fun testExportsBare() = variants("module M { exports <caret> }", "pkg")
  fun testExportsPrefixed() = complete("module M { exports p<caret> }", "module M { exports pkg.<caret> }")
  fun testExportsQualified() = variants("module M { exports pkg.<caret> }", "main", "other", "empty")
  fun testExportsQualifiedUnambiguous() = complete("module M { exports pkg.o<caret> }", "module M { exports pkg.other.<caret> }")
  fun testExportsTo() = complete("module M { exports pkg.other <caret> }", "module M { exports pkg.other to <caret> }")
  fun testExportsToList() = variants("module M { exports pkg.other to <caret> }", "M2", "java.base", "lib.multi.release", "lib.named")
  fun testExportsToUnambiguous() = complete("module M { exports pkg.other to M<caret> }", "module M { exports pkg.other to M2<caret> }")

  fun testUsesPrefixed() = complete("module M { uses p<caret> }", "module M { uses pkg.<caret> }")
  fun testUsesQualified1() = variants("module M { uses pkg.<caret> }", "main", "other", "empty")
  fun testUsesQualified2() = variants("module M { uses pkg.main.<caret> }", "MyAnno", "MySvc")
  fun testUsesUnambiguous() = complete("module M { uses pkg.main.MS<caret> }", "module M { uses pkg.main.MySvc<caret> }")

  fun testProvidesPrefixed() = complete("module M { provides p<caret> }", "module M { provides pkg.<caret> }")
  fun testProvidesQualified1() = variants("module M { provides pkg.<caret> }", "main", "other", "empty")
  fun testProvidesQualified2() = variants("module M { provides pkg.main.<caret> }", "MyAnno", "MySvc")
  fun testProvidesUnambiguous() = complete("module M { provides pkg.main.MS<caret> }", "module M { provides pkg.main.MySvc<caret> }")
  fun testProvidesWith() = complete("module M { provides pkg.main.MySvc <caret> }", "module M { provides pkg.main.MySvc with <caret> }")
  fun testProvidesWithPrefixed() =
    complete("module M { provides pkg.main.MySvc with p<caret> }", "module M { provides pkg.main.MySvc with pkg.<caret> }")
  fun testProvidesWithQualified() =
    complete("module M { provides pkg.main.MySvc with pkg.o<caret> }", "module M { provides pkg.main.MySvc with pkg.other.<caret> }")
  fun testProvidesWithUnambiguous() =
    complete("module M { provides pkg.main.MySvc with pkg.other.M<caret> }", "module M { provides pkg.main.MySvc with pkg.other.MySvcImpl<caret> }")

  fun testImports() {
    addFile("module-info.java", "module M { requires M2; }")
    addFile("module-info.java", "module M2 { exports pkg.m2; }", M2)
    addFile("pkg/m2/C2.java", "package pkg.m2;\npublic class C2 { }", M2)
    addFile("pkg/m2/impl/C2Impl.java", "package pkg.m2.impl;\npublic class C2Impl { }", M2)
    myFixture.configureByText("test.java", "import pkg.m2.<caret>")
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).containsExactly("*", "C2")  // no 'C2Impl'
  }

  //<editor-fold desc="Helpers.">
  private fun complete(text: String, expected: String) {
    myFixture.configureByText("module-info.java", text)
    myFixture.completeBasic()
    myFixture.checkResult(expected)
  }

  private fun variants(text: String, vararg variants: String) {
    myFixture.configureByText("module-info.java", text)
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).containsExactlyInAnyOrder(*variants)
  }
  //</editor-fold>
}