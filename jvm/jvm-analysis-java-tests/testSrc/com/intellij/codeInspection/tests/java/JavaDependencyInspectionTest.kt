package com.intellij.codeInspection.tests.java

import com.intellij.jvm.analysis.internal.testFramework.dependencyInspectionFixture
import com.intellij.jvm.analysis.testFramework.jvmProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class JavaDependencyInspectionTest {
  companion object {
    private val projectFixture = jvmProjectFixture()
  }

  private val myFixture by dependencyInspectionFixture(projectFixture)

  @Test
  fun `test illegal imported dependency Java API`() = myFixture.dependencyViolationTest(myFixture.javaFooFile, "ImportClientJava.java", """
      package pkg.client;
      
      import <error descr="Dependency rule 'Deny usages of scope 'JavaFoo' in scope 'ImportClientJava'.' is violated">pkg.api.JavaFoo</error>;

      class Client {
        public static void main(String[] args) {
          new <error descr="Dependency rule 'Deny usages of scope 'JavaFoo' in scope 'ImportClientJava'.' is violated">JavaFoo</error>();
        } 
      }            
    """.trimIndent())

  @Test
  fun `test illegal imported dependency skip imports`() = myFixture.dependencyViolationTest(myFixture.javaFooFile, "ImportClientJava.java", """
      package pkg.client;
      
      import pkg.api.JavaFoo;

      class Client {
        public static void main(String[] args) {
          new <error descr="Dependency rule 'Deny usages of scope 'JavaFoo' in scope 'ImportClientJava'.' is violated">JavaFoo</error>();
        } 
      }            
    """.trimIndent(), skipImports = true)

  @Test
  fun `test illegal imported dependency skip static imports`() = myFixture.dependencyViolationTest(myFixture.javaFooFile, "ImportClientJava.java", """
      package pkg.client;
      
      import static pkg.api.JavaFoo.foo;

      class Client {
        public static void main(String[] args) {
          <error descr="Dependency rule 'Deny usages of scope 'JavaFoo' in scope 'ImportClientJava'.' is violated">foo</error>();
        } 
      }            
    """.trimIndent(), skipImports = true)

  @Test
  fun `test illegal imported dependency qualified expression`() = myFixture.dependencyViolationTest(myFixture.javaFooFile, "ImportClientJava.java", """
      package pkg.client;
      
      import pkg.api.JavaFoo;

      class Client {
        public static void main(String[] args) {
          <error descr="Dependency rule 'Deny usages of scope 'JavaFoo' in scope 'ImportClientJava'.' is violated">JavaFoo.foo</error>();
        } 
      }            
    """.trimIndent(), skipImports = true)

  @Test
  fun `test illegal fully qualified dependency Java API`() = myFixture.dependencyViolationTest(myFixture.javaFooFile, "FqClientJava.java", """
      package pkg.client;
      
      class Client {
        public static void main(String[] args) {
          new <error descr="Dependency rule 'Deny usages of scope 'JavaFoo' in scope 'FqClientJava'.' is violated">pkg.api.JavaFoo</error>();
        } 
      }      
    """.trimIndent())
}