package com.intellij.codeInspection.tests.java

import com.intellij.codeInspection.tests.DependencyInspectionTestBase

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

  fun `test illegal imported dependency skip imports`() = dependencyViolationTest(javaFooFile, "ImportClientJava.java", """
      package pkg.client;
      
      import pkg.api.JavaFoo;

      class Client {
        public static void main(String[] args) {
          new <error descr="Dependency rule 'Deny usages of scope 'JavaFoo' in scope 'ImportClientJava'.' is violated">JavaFoo</error>();
        } 
      }            
    """.trimIndent(), skipImports = true)

  fun `test illegal imported dependency skip static imports`() = dependencyViolationTest(javaFooFile, "ImportClientJava.java", """
      package pkg.client;
      
      import static pkg.api.JavaFoo.foo;

      class Client {
        public static void main(String[] args) {
          <error descr="Dependency rule 'Deny usages of scope 'JavaFoo' in scope 'ImportClientJava'.' is violated">foo</error>();
        } 
      }            
    """.trimIndent(), skipImports = true)

  fun `test illegal imported dependency qualified expression`() = dependencyViolationTest(javaFooFile, "ImportClientJava.java", """
      package pkg.client;
      
      import pkg.api.JavaFoo;

      class Client {
        public static void main(String[] args) {
          <error descr="Dependency rule 'Deny usages of scope 'JavaFoo' in scope 'ImportClientJava'.' is violated">JavaFoo.foo</error>();
        } 
      }            
    """.trimIndent(), skipImports = true)

  fun `test illegal fully qualified dependency Java API`() = dependencyViolationTest(javaFooFile, "FqClientJava.java", """
      package pkg.client;
      
      class Client {
        public static void main(String[] args) {
          new <error descr="Dependency rule 'Deny usages of scope 'JavaFoo' in scope 'FqClientJava'.' is violated">pkg.api.JavaFoo</error>();
        } 
      }      
    """.trimIndent())
}