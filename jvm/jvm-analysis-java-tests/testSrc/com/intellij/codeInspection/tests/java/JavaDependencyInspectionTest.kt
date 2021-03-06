package com.intellij.codeInspection.tests.java

import com.intellij.codeInspection.tests.DependencyInspectionTestBase
import com.intellij.packageDependencies.DependencyRule
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.PackageSetFactory

class JavaDependencyInspectionTest : DependencyInspectionTestBase() {
  override fun setUp() {
    super.setUp()
    myFixture.addFileToProject("$clientFileNameImport.java", """
      package com.jetbrains.test.client;
      
      import <error descr="$errorMessage">com.jetbrains.test.api.Foo</error>;
      
      class $clientFileNameImport {
        public static void main(String[] args) {
          new <error descr="$errorMessage">Foo</error>();
        } 
      }
    """.trimIndent())
    myFixture.addFileToProject("$clientFileNameFq.java", """
      package com.jetbrains.test.client;
      
      class $clientFileNameFq {
        public static void main(String[] args) {
          new <error descr="$errorMessage">com.jetbrains.test.api.Foo</error>();
        } 
      }
    """.trimIndent())
    myFixture.addFileToProject("$apiFileName.java", """
      package com.jetbrains.test.api;
      
      public class Foo() { };
    """.trimIndent())

    val importFileScope = NamedScope(clientFileName, PackageSetFactory.getInstance().compile("file:$clientFileNameImport.java"))
    val fqFileScope = NamedScope(clientFileName, PackageSetFactory.getInstance().compile("file:$clientFileNameFq.java"))
    val libraryScope = NamedScope(apiFileName, PackageSetFactory.getInstance().compile("file:$apiFileName.java"))
    DependencyValidationManager.getInstance(project).apply {
      addRule(DependencyRule(importFileScope, libraryScope, true))
      addRule(DependencyRule(fqFileScope, libraryScope, true))
    }
  }

  fun `test illegal imported dependency`() {
    myFixture.testHighlighting("$clientFileNameImport.java")
  }

  fun `test illegal fully qualified dependency`() {
    myFixture.testHighlighting("$clientFileNameFq.java")
  }

  companion object {
    const val clientFileName: String = "ClientFile"
    const val clientFileNameImport: String = "${clientFileName}Import"
    const val clientFileNameFq: String = "${clientFileName}Fq"
    const val apiFileName: String = "ApiFile"
    const val errorMessage = "Dependency rule 'Deny usages of scope '$apiFileName' in scope '$clientFileName'.' is violated"
  }
}