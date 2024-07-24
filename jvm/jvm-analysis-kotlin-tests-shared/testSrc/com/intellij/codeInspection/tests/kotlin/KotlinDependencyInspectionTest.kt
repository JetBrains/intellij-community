package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.internal.testFramework.DependencyInspectionTestBase
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider

abstract class KotlinDependencyInspectionTest : DependencyInspectionTestBase(), KotlinPluginModeProvider {
  fun `test illegal imported dependency Java API`() = dependencyViolationTest(javaFooFile, "ImportClientJava.kt", """
      package pkg.client
      
      import <error descr="Dependency rule 'Deny usages of scope 'JavaFoo' in scope 'ImportClientJava'.' is violated">pkg.api.JavaFoo</error>
      
      fun main() {
        <error descr="Dependency rule 'Deny usages of scope 'JavaFoo' in scope 'ImportClientJava'.' is violated">JavaFoo()</error>
      }
    """.trimIndent())

  fun `test illegal imported dependency Kotlin API`() = dependencyViolationTest(kotlinFooFile, "ImportClientKotlin.kt", """
      package pkg.client
      
      import <error descr="Dependency rule 'Deny usages of scope 'KotlinFoo' in scope 'ImportClientKotlin'.' is violated">pkg.api.KotlinFoo</error>
      
      fun main() {
        <error descr="Dependency rule 'Deny usages of scope 'KotlinFoo' in scope 'ImportClientKotlin'.' is violated">KotlinFoo()</error>
      }
    """.trimIndent())

  fun `test illegal imported dependency skip imports`() = dependencyViolationTest(kotlinFooFile, "ImportClientKotlin.kt", """
      package pkg.client
      
      import pkg.api.KotlinFoo
      
      fun main() {
        <error descr="Dependency rule 'Deny usages of scope 'KotlinFoo' in scope 'ImportClientKotlin'.' is violated">KotlinFoo()</error>
      }
    """.trimIndent(), skipImports = true)

  fun `test illegal imported dependency Kotlin API in Java`() = dependencyViolationTest(kotlinFooFile, "ImportClientKotlin.java", """
      package pkg.client;
      
      import <error descr="Dependency rule 'Deny usages of scope 'KotlinFoo' in scope 'ImportClientKotlin'.' is violated">pkg.api.KotlinFoo</error>;

      class Client {
        public static void main(String[] args) {
          new <error descr="Dependency rule 'Deny usages of scope 'KotlinFoo' in scope 'ImportClientKotlin'.' is violated">KotlinFoo</error>();
        } 
      }    
    """.trimIndent())

  fun `test illegal fully qualified dependency Java API`() = dependencyViolationTest(javaFooFile, "FqClientJava.kt", """
      package pkg.client
      
      fun main() {
        <error descr="Dependency rule 'Deny usages of scope 'JavaFoo' in scope 'FqClientJava'.' is violated">pkg.api.JavaFoo()</error>
      }
    """.trimIndent())

  fun `test illegal fully qualified dependency Kotlin API`() = dependencyViolationTest(kotlinFooFile, "FqClientKotlin.kt", """
      package pkg.client
      
      fun main() {
        <error descr="Dependency rule 'Deny usages of scope 'KotlinFoo' in scope 'FqClientKotlin'.' is violated">pkg.api.KotlinFoo()</error>
      }
    """.trimIndent())

  fun `test illegal fully qualified dependency Kotlin API in Java`() = dependencyViolationTest(kotlinFooFile, "FqClientKotlin.java", """
      package pkg.client;
      
      class Client {
        public static void main(String[] args) {
          new  <error descr="Dependency rule 'Deny usages of scope 'KotlinFoo' in scope 'FqClientKotlin'.' is violated">pkg.api.KotlinFoo</error>();
        } 
      }      
    """.trimIndent())
}