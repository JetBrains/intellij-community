package com.intellij.codeInspection

import com.intellij.codeInspection.tests.DependencyInspectionTestBase
import com.intellij.packageDependencies.DependencyRule
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.PackageSetFactory

class KotlinDependencyInspectionTest : DependencyInspectionTestBase() {
  override fun setUp() {
    super.setUp()
    myFixture.addFileToProject("$clientFileNameImport.kt", """
      package com.jetbrains.test.client
      
      import <error descr="$errorMessage">com.jetbrains.test.api.Foo</error>
      
      fun main() {
        <error descr="$errorMessage">Foo()</error>
      }
    """.trimIndent())
    myFixture.addFileToProject("$clientFileNameFq.kt", """
      package com.jetbrains.test.client
      
      fun main() {
        <error descr="$errorMessage">com.jetbrains.test.api.Foo()</error>
      }
    """.trimIndent())
    myFixture.addFileToProject("$apiFileName.kt", """
      package com.jetbrains.test.api
      
      class Foo()
    """.trimIndent())
    val importFileScope = NamedScope(clientFileName, PackageSetFactory.getInstance().compile("file:$clientFileNameImport.kt"))
    val fqFileScope = NamedScope(clientFileName, PackageSetFactory.getInstance().compile("file:$clientFileNameFq.kt"))
    val libraryScope = NamedScope(apiFileName, PackageSetFactory.getInstance().compile("file:$apiFileName.kt"))
    DependencyValidationManager.getInstance(project).apply {
      addRule(DependencyRule(importFileScope, libraryScope, true))
      addRule(DependencyRule(fqFileScope, libraryScope, true))
    }
  }

  fun `test illegal imported dependency`() {
    myFixture.testHighlighting("$clientFileNameImport.kt")
  }

  fun `test illegal fully qualified dependency`() {
    myFixture.testHighlighting("$clientFileNameFq.kt")
  }

  companion object {
    const val clientFileName: String = "ClientFile"
    const val clientFileNameImport: String = "${clientFileName}Import"
    const val clientFileNameFq: String = "${clientFileName}Fq"
    const val apiFileName: String = "ApiFile"
    const val errorMessage = "Dependency rule 'Deny usages of scope '$apiFileName' in scope '$clientFileName'.' is violated"
  }
}