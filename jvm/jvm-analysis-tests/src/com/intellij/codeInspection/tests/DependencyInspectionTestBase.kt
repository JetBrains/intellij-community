package com.intellij.codeInspection.tests

import com.intellij.codeInspection.DependencyInspection
import com.intellij.packageDependencies.DependencyRule
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.PackageSetFactory

abstract class DependencyInspectionTestBase : UastInspectionTestBase() {
  override val inspection = DependencyInspection()

  protected val javaFoo = "JavaFoo.java"

  protected val kotlinFoo = "KotlinFoo.kt"

  override fun setUp() {
    super.setUp()
    myFixture.addFileToProject("pkg/api/$javaFoo", """
      package pkg.api;
      
      public class JavaFoo() { };
    """.trimIndent())
    myFixture.addFileToProject("pkg/api/$kotlinFoo", """
      package pkg.api
      
      public class KotlinFoo()
    """.trimIndent())
  }

  protected fun dependencyViolationTest(fromFile: String, toFile: String, toFileText: String) {
    val dependencyValidationManager = DependencyValidationManager.getInstance(project)
    myFixture.addFileToProject("pkg/client/$toFile", toFileText)
    val fromScopeId = fromFile.substringBefore(".")
    val fromScope = dependencyValidationManager.getScope(fromScopeId) ?: NamedScope(
      fromScopeId, PackageSetFactory.getInstance().compile("file:pkg/api/$fromFile")
    )
    val toScope = NamedScope(toFile.substringBefore("."), PackageSetFactory.getInstance().compile("file:pkg/client/$toFile"))
    dependencyValidationManager.apply {
      addRule(DependencyRule(toScope, fromScope, true))
    }
    myFixture.testHighlighting("pkg/client/$toFile")
  }
}