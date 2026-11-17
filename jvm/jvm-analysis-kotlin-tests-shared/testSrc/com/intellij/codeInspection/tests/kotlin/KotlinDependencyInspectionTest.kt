package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.internal.testFramework.dependencyInspectionFixture
import com.intellij.jvm.analysis.testFramework.jvmProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
abstract class KotlinDependencyInspectionTest {
  companion object {
    private val projectFixture = jvmProjectFixture()
  }

  protected val myFixture by dependencyInspectionFixture(projectFixture)

  

  @Test
  fun `test illegal imported dependency Java API`() = myFixture.dependencyViolationTest(myFixture.javaFooFile, "ImportClientJava.kt", """
      package pkg.client
      
      import <error descr="Dependency rule 'Deny usages of scope 'JavaFoo' in scope 'ImportClientJava'.' is violated">pkg.api.JavaFoo</error>
      
      fun main() {
        <error descr="Dependency rule 'Deny usages of scope 'JavaFoo' in scope 'ImportClientJava'.' is violated">JavaFoo()</error>
      }
    """.trimIndent())

  @Test
  fun `test illegal imported dependency Kotlin API`() = myFixture.dependencyViolationTest(myFixture.kotlinFooFile, "ImportClientKotlin.kt", """
      package pkg.client
      
      import <error descr="Dependency rule 'Deny usages of scope 'KotlinFoo' in scope 'ImportClientKotlin'.' is violated">pkg.api.KotlinFoo</error>
      
      fun main() {
        <error descr="Dependency rule 'Deny usages of scope 'KotlinFoo' in scope 'ImportClientKotlin'.' is violated">KotlinFoo()</error>
      }
    """.trimIndent())

  @Test
  fun `test illegal imported dependency skip imports`() = myFixture.dependencyViolationTest(myFixture.kotlinFooFile, "ImportClientKotlin.kt", """
      package pkg.client
      
      import pkg.api.KotlinFoo
      
      fun main() {
        <error descr="Dependency rule 'Deny usages of scope 'KotlinFoo' in scope 'ImportClientKotlin'.' is violated">KotlinFoo()</error>
      }
    """.trimIndent(), skipImports = true)

  @Test
  fun `test illegal imported dependency Kotlin API in Java`() = myFixture.dependencyViolationTest(myFixture.kotlinFooFile, "ImportClientKotlin.java", """
      package pkg.client;
      
      import <error descr="Dependency rule 'Deny usages of scope 'KotlinFoo' in scope 'ImportClientKotlin'.' is violated">pkg.api.KotlinFoo</error>;

      class Client {
        public static void main(String[] args) {
          new <error descr="Dependency rule 'Deny usages of scope 'KotlinFoo' in scope 'ImportClientKotlin'.' is violated">KotlinFoo</error>();
        } 
      }    
    """.trimIndent())

  @Test
  fun `test illegal fully qualified dependency Java API`() = myFixture.dependencyViolationTest(myFixture.javaFooFile, "FqClientJava.kt", """
      package pkg.client
      
      fun main() {
        <error descr="Dependency rule 'Deny usages of scope 'JavaFoo' in scope 'FqClientJava'.' is violated">pkg.api.JavaFoo()</error>
      }
    """.trimIndent())

  @Test
  fun `test illegal fully qualified dependency Kotlin API`() = myFixture.dependencyViolationTest(myFixture.kotlinFooFile, "FqClientKotlin.kt", """
      package pkg.client
      
      fun main() {
        <error descr="Dependency rule 'Deny usages of scope 'KotlinFoo' in scope 'FqClientKotlin'.' is violated">pkg.api.KotlinFoo()</error>
      }
    """.trimIndent())

  @Test
  fun `test illegal fully qualified dependency Kotlin API in Java`() = myFixture.dependencyViolationTest(myFixture.kotlinFooFile, "FqClientKotlin.java", """
      package pkg.client;
      
      class Client {
        public static void main(String[] args) {
          new  <error descr="Dependency rule 'Deny usages of scope 'KotlinFoo' in scope 'FqClientKotlin'.' is violated">pkg.api.KotlinFoo</error>();
        } 
      }      
    """.trimIndent())
}