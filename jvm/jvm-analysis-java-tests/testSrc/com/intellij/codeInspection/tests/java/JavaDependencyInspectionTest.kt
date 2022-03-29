package com.intellij.codeInspection.tests.java

import com.intellij.codeInspection.tests.DependencyInspectionTestBase
import org.junit.Ignore

class JavaDependencyInspectionTest : DependencyInspectionTestBase() {
  fun `test illegal imported dependency Java API`() = dependencyViolationTest(javaFooFile, "ImportClientJava.java", """
      package pkg.client;
      
      import <error descr="Dependency rule 'Deny usages of scope 'JavaFoo' in scope 'ImportClientJava'.' is violated">pkg.api.JavaFoo</error>;

      class Client {
        public static void main(String[] args) {
          new <error descr="Dependency rule 'Deny usages of scope 'JavaFoo' in scope 'ImportClientJava'.' is violated">JavaFoo</error>();
        } 
      }            
    """.trimIndent())

  @Ignore
  fun `test illegal imported dependency Kotlin API`() = dependencyViolationTest(kotlinFooFile, "ImportClientKotlin.java", """
      package pkg.client;
      
      import <error descr="Dependency rule 'Deny usages of scope 'KotlinFoo' in scope 'ImportClientKotlin'.' is violated">pkg.api.KotlinFoo</error>;

      class Client {
        public static void main(String[] args) {
          new <error descr="Dependency rule 'Deny usages of scope 'KotlinFoo' in scope 'ImportClientKotlin'.' is violated">KotlinFoo</error>();
        } 
      }    
    """.trimIndent())

  fun `test illegal fully qualified dependency Java API`() = dependencyViolationTest(javaFooFile, "FqClientJava.java", """
      package pkg.client;
      
      class Client {
        public static void main(String[] args) {
          new <error descr="Dependency rule 'Deny usages of scope 'JavaFoo' in scope 'FqClientJava'.' is violated">pkg.api.JavaFoo</error>();
        } 
      }      
    """.trimIndent())

  @Ignore
  fun `test illegal fully qualified dependency Kotlin API`() = dependencyViolationTest(kotlinFooFile, "FqClientKotlin.java", """
      package pkg.client;
      
      class Client {
        public static void main(String[] args) {
          new  <error descr="Dependency rule 'Deny usages of scope 'KotlinFoo' in scope 'FqClientKotlin'.' is violated">pkg.api.KotlinFoo</error>();
        } 
      }      
    """.trimIndent())
}