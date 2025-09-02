package com.intellij.jvm.analysis.internal.testFramework

import com.intellij.codeInspection.DependencyInspection
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase
import com.intellij.packageDependencies.DependencyRule
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.PackageSetFactory

abstract class DependencyInspectionTestBase : JvmInspectionTestBase() {
  override val inspection: DependencyInspection = DependencyInspection()

  protected val javaFooFile: String = "JavaFoo.java"

  protected val kotlinFooFile: String = "KotlinFoo.kt"

  override fun setUp() {
    super.setUp()
    myFixture.addFileToProject("pkg/api/$javaFooFile", """
      package pkg.api;
      
      public class JavaFoo() {
          public static void foo() { } 
      };
    """.trimIndent())
    myFixture.addFileToProject("pkg/api/$kotlinFooFile", """
      package pkg.api
      
      public class KotlinFoo()
    """.trimIndent())
  }

  protected fun dependencyViolationTest(fromFile: String, toFile: String, toFileText: String, skipImports: Boolean = false) {
    val dependencyValidationManager = DependencyValidationManager.getInstance(project)
    dependencyValidationManager.setSkipImportStatements(skipImports)
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
