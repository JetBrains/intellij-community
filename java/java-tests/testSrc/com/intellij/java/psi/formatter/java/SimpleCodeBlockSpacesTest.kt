// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.formatter.java

class SimpleCodeBlockSpacesTest : AbstractJavaFormatterTest() {
  override fun setUp() {
    super.setUp()
    settings.SPACE_WITHIN_BRACES = false
    settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true
    settings.KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = true
    settings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = true
  }


  fun `test should add spaces inside braces when block body is presented`() {
    javaSettings.SPACES_INSIDE_BLOCK_BRACES_WHEN_BODY_IS_PRESENTED = true
    doTextTest(
      """
      class A {
      public void simpleMethod() {int x = 10;}
      
      public void complexMethod() {
         try {int x = 10;} catch (IOException e) {int y = 20;}
         
         Runnable r = () -> {int z = 30;};
      }
      }
      """.trimIndent(),
      """
      class A {
          public void simpleMethod() { int x = 10; }
      
          public void complexMethod() {
              try { int x = 10; } catch (IOException e) { int y = 20; }
      
              Runnable r = () -> { int z = 30; };
          }
      }
      """.trimIndent())
  }



  fun `test should not add spaces inside braces when block body is empty`() {
    javaSettings.SPACES_INSIDE_BLOCK_BRACES_WHEN_BODY_IS_PRESENTED = true
    doTextTest(
      """
      class A {
      public void simpleMethod() {}
      
      public void complexMethod() {
         try {} catch (IOException e) {}
         
         Runnable r = () -> {};
      }
      }
      """.trimIndent(),
      """
      class A {
          public void simpleMethod() {}
      
          public void complexMethod() {
              try {} catch (IOException e) {}
      
              Runnable r = () -> {};
          }
      }
      """.trimIndent())
  }

  fun `test should not add spaces inside braces when block body is present and option is disabled`() {
    javaSettings.SPACES_INSIDE_BLOCK_BRACES_WHEN_BODY_IS_PRESENTED = false
    doTextTest(
      """
      class A {
      public void simpleMethod() { int x = 10; }
      
      public void complexMethod() {
         try { int x = 10; } catch (IOException e) { int y = 20; }
         
         Runnable r = () -> { int z = 30; };
      }
      }
      """.trimIndent(),
      """
      class A {
          public void simpleMethod() {int x = 10;}
      
          public void complexMethod() {
              try {int x = 10;} catch (IOException e) {int y = 20;}
      
              Runnable r = () -> {int z = 30;};
          }
      }
      """.trimIndent())
  }

  fun `test non empty simple class is always formatted to a new line`() {
    settings.KEEP_SIMPLE_CLASSES_IN_ONE_LINE = true;
    doTextTest(
      """
      class A {
          static class B {int x;}
          
          static class C {void foo() {}}
          
          static class D {static {}}

          static class E {class X{}}

          static class F {}
      }
      """.trimIndent(),
      """
      class A {
          static class B {
              int x;
          }

          static class C {
              void foo() {}
          }

          static class D {
              static {}}

          static class E {
              class X {}
          }

          static class F {}
      }
      """.trimIndent())
  }
}