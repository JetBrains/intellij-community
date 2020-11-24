package com.intellij.codeInspection

import com.intellij.codeInspection.tests.DependencyInspectionTestBase
import com.intellij.packageDependencies.DependencyRule
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.PackageSetFactory

class KotlinDependencyInspectionTest : DependencyInspectionTestBase() {
  override fun setUp() {
    super.setUp()
    myFixture.addFileToProject("$clientFileName.kt", """
      package com.jetbrains.test.client
      
      import com.jetbrains.test.api.<error descr="$errorMessage">Foo</error>
      
      fun main() {
        <error descr="$errorMessage">Foo</error>()
      }
    """.trimIndent())
    myFixture.addFileToProject("$apiFileName.kt", """
      package com.jetbrains.test.api
      
      class Foo()
    """.trimIndent())
  }

  fun `test dependency`() {
    val clientScope = NamedScope(clientFileName, PackageSetFactory.getInstance().compile("file:$clientFileName.kt"))
    val libraryScope = NamedScope(apiFileName, PackageSetFactory.getInstance().compile("file:$apiFileName.kt"))
    DependencyValidationManager.getInstance(project).addRule(DependencyRule(clientScope, libraryScope, true))
    myFixture.testHighlighting("$clientFileName.kt")
  }

  companion object {
    const val clientFileName: String = "ClientFile"
    const val apiFileName: String = "ApiFile"
    const val errorMessage = "Dependency rule 'Deny usages of scope '$apiFileName' in scope '$clientFileName'.' is violated"
  }
}