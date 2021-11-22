package com.intellij.codeInspection.tests.java

import com.intellij.codeInspection.tests.DependencyInspectionTestBase

class JavaDependencyInspectionTest : DependencyInspectionTestBase() {
  fun `test illegal imported dependency Java API`() = dependencyViolationTest(javaFoo, "ImportClientJava.java", """
      package pkg.client;
      
      import <error descr="Dependency rule 'Deny usages of scope 'JavaFoo' in scope 'ImportClientJava'.' is violated">pkg.api.JavaFoo</error>;

      class Client {
        public static void main(String[] args) {
          new <error descr="Dependency rule 'Deny usages of scope 'JavaFoo' in scope 'ImportClientJava'.' is violated">JavaFoo</error>();
        } 
      }            
    """.trimIndent())

  // TODO, Kotlin code is not resolved?
  fun `ignore test illegal imported dependency Kotlin API`() = dependencyViolationTest(kotlinFoo, "ImportClientKotlin.java", """
      package pkg.client;
      
      import pkg.api.KotlinFoo;

      class Client {
        public static void main(String[] args) {
          new KotlinFoo();
        } 
      }    
    """.trimIndent())

  fun `test illegal fully qualified dependency Java API`() = dependencyViolationTest(javaFoo, "FqClientJava.java", """
      package pkg.client;
      
      class Client {
        public static void main(String[] args) {
          new <error descr="Dependency rule 'Deny usages of scope 'JavaFoo' in scope 'FqClientJava'.' is violated">pkg.api.JavaFoo</error>();
        } 
      }      
    """.trimIndent())

  // TODO, Kotlin code is not resolved?
  fun `ignore test illegal fully qualified dependency Kotlin API`() = dependencyViolationTest(kotlinFoo, "FqClientKotlin.java", """
      package pkg.client;
      
      class Client {
        public static void main(String[] args) {
          new pkg.api.KotlinFoo();
        } 
      }      
    """.trimIndent())
}