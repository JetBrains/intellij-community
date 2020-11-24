package com.intellij.codeInspection.tests.java

import com.intellij.codeInspection.tests.DependencyInspectionTestBase
import com.intellij.packageDependencies.DependencyRule
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.PackageSetFactory

class JavaDependencyInspectionTest : DependencyInspectionTestBase() {
  override fun setUp() {
    super.setUp()
    myFixture.addFileToProject("$clientFileName.java", """
      package com.jetbrains.test.client;
      
      import com.jetbrains.test.api.<error descr="$errorMessage">Foo</error>;
      
      class Client {
        public static void main(String[] args) {
          new <error descr="$errorMessage">Foo</error>();
        } 
      }
    """.trimIndent())
    myFixture.addFileToProject("$apiFileName.java", """
      package com.jetbrains.test.api;
      
      public class Foo() { };
    """.trimIndent())
  }

  fun `test dependency`() {
    val clientScope = NamedScope(clientFileName, PackageSetFactory.getInstance().compile("file:$clientFileName.java"))
    val libraryScope = NamedScope(apiFileName, PackageSetFactory.getInstance().compile("file:$apiFileName.java"))
    DependencyValidationManager.getInstance(project).addRule(DependencyRule(clientScope, libraryScope, true))
    myFixture.testHighlighting("$clientFileName.java")
  }

  companion object {
    const val clientFileName: String = "ClientFile"
    const val apiFileName: String = "ApiFile"
    const val errorMessage = "Dependency rule 'Deny usages of scope '$apiFileName' in scope '$clientFileName'.' is violated"
  }
}