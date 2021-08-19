package com.intellij.codeInspection.tests.kotlin

import com.intellij.codeInspection.tests.DependencyInspectionTestBase
import com.intellij.packageDependencies.DependencyRule
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.PackageSetFactory

class KotlinDependencyInspectionTest : DependencyInspectionTestBase() {
  override fun setUp() {
    super.setUp()
    myFixture.addFileToProject(apiFileName, """
      package com.jetbrains.test.api
      
      class Foo()
    """.trimIndent())
    myFixture.addFileToProject(clientFileNameImport, """
      package com.jetbrains.test.client
      
      import <error descr="$errorMessage">com.jetbrains.test.api.Foo</error>
      
      fun main() {
        <error descr="$errorMessage">Foo()</error>
      }
    """.trimIndent())
    myFixture.addFileToProject(clientFileNameFq, """
      package com.jetbrains.test.client
      
      fun main() {
        <error descr="$errorMessage">com.jetbrains.test.api.Foo()</error>
      }
    """.trimIndent())
    val importFileScope = NamedScope(clientFileName, PackageSetFactory.getInstance().compile("file:$clientFileNameImport"))
    val fqFileScope = NamedScope(clientFileName, PackageSetFactory.getInstance().compile("file:$clientFileNameFq"))
    val libraryScope = NamedScope(apiFileName, PackageSetFactory.getInstance().compile("file:$apiFileName"))
    DependencyValidationManager.getInstance(project).apply {
      addRule(DependencyRule(importFileScope, libraryScope, true))
      addRule(DependencyRule(fqFileScope, libraryScope, true))
    }
  }

  fun `test illegal imported dependency`() {
    myFixture.testHighlighting(clientFileNameImport)
  }

  fun `test illegal fully qualified dependency`() {
    myFixture.testHighlighting(clientFileNameFq)
  }

  companion object {
    const val clientFileName: String = "ClientFile"
    const val clientFileNameImport: String = "ClientFileImport.kt"
    const val clientFileNameFq: String = "ClientFileFq.kt"
    const val apiFileName: String = "ApiFile.kt"
    const val errorMessage = "Dependency rule 'Deny usages of scope '$apiFileName' in scope '$clientFileName'.' is violated"
  }
}