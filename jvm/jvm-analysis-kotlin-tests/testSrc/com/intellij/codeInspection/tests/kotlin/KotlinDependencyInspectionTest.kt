package com.intellij.codeInspection.tests.kotlin

import com.intellij.codeInspection.tests.DependencyInspectionTestBase

class KotlinDependencyInspectionTest0 : DependencyInspectionTestBase() {
  fun `test illegal imported dependency Java API`() = dependencyViolationTest(javaFoo, "ImportClientJava.kt", """
      package pkg.client
      
      import <error descr="Dependency rule 'Deny usages of scope 'JavaFoo' in scope 'ImportClientJava'.' is violated">pkg.api.JavaFoo</error>
      
      fun main() {
        <error descr="Dependency rule 'Deny usages of scope 'JavaFoo' in scope 'ImportClientJava'.' is violated">JavaFoo()</error>
      }
    """.trimIndent())

  fun `test illegal imported dependency Kotlin API`() = dependencyViolationTest(kotlinFoo, "ImportClientKotlin.kt", """
      package pkg.client
      
      import <error descr="Dependency rule 'Deny usages of scope 'KotlinFoo' in scope 'ImportClientKotlin'.' is violated">pkg.api.KotlinFoo</error>
      
      fun main() {
        <error descr="Dependency rule 'Deny usages of scope 'KotlinFoo' in scope 'ImportClientKotlin'.' is violated">KotlinFoo()</error>
      }
    """.trimIndent())

  fun `test illegal fully qualified dependency Java API`() = dependencyViolationTest(javaFoo, "FqClientJava.kt", """
      package pkg.client
      
      fun main() {
        <error descr="Dependency rule 'Deny usages of scope 'JavaFoo' in scope 'FqClientJava'.' is violated">pkg.api.JavaFoo()</error>
      }
    """.trimIndent())

  fun `test illegal fully qualified dependency Kotlin API`() = dependencyViolationTest(kotlinFoo, "FqClientKotlin.kt", """
      package pkg.client
      
      fun main() {
        <error descr="Dependency rule 'Deny usages of scope 'KotlinFoo' in scope 'FqClientKotlin'.' is violated">pkg.api.KotlinFoo()</error>
      }
    """.trimIndent())
}