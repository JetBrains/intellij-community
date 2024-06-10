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


  fun `test should add spaces inside braces when block body is present`() {
    javaSettings.SPACES_INSIDE_BLOCK_BRACES_WHEN_BODY_IS_PRESENT = true
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

  fun `test option doesn't affect code blocks when SPACES_WITHIN_BRACES is enabled`() {
    settings.SPACE_WITHIN_BRACES = true
    settings.KEEP_SIMPLE_CLASSES_IN_ONE_LINE = true
    val expected =
    """
    class A {
        static class B {}
    
        void emptyMethod() {}

        void simpleMethod() {int x = 10;}
  
        void complexMethod() {
          try {} catch (IOException e) {}
          try {int x = 10;} catch (IOException e) {int y = 20;}
          
          Runnable r = () -> {};
          Runnable v = () -> {int x = 10;};
        }
    }
    """.trimIndent()
    val actual =
    """
    class A {
        static class B { }
    
        void emptyMethod() { }

        void simpleMethod() { int x = 10; }
  
        void complexMethod() {
            try { } catch (IOException e) { }
            try { int x = 10; } catch (IOException e) { int y = 20; }

            Runnable r = () -> { };
            Runnable v = () -> { int x = 10; };
        }
    }
    """.trimIndent()

    javaSettings.SPACES_INSIDE_BLOCK_BRACES_WHEN_BODY_IS_PRESENT = false
    doTextTest(expected, actual)
    javaSettings.SPACES_INSIDE_BLOCK_BRACES_WHEN_BODY_IS_PRESENT = true
    doTextTest(expected, actual)
  }

  fun `test option doesn't affect code blocks with empty body`() {
    val expected = """
      class A {
      public void simpleMethod() {}
      
      public void complexMethod() {
         try {} catch (IOException e) {}
         
         Runnable r = () -> {};
      }
      }
      """.trimIndent()
    val actual = """
      class A {
          public void simpleMethod() {}
      
          public void complexMethod() {
              try {} catch (IOException e) {}
      
              Runnable r = () -> {};
          }
      }
      """.trimIndent()
    javaSettings.SPACES_INSIDE_BLOCK_BRACES_WHEN_BODY_IS_PRESENT = false
    doTextTest(expected, actual)
    javaSettings.SPACES_INSIDE_BLOCK_BRACES_WHEN_BODY_IS_PRESENT = true
    doTextTest(expected, actual)
  }

  fun `test should not add spaces inside braces when block body is present and option is disabled`() {
    javaSettings.SPACES_INSIDE_BLOCK_BRACES_WHEN_BODY_IS_PRESENT = false
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
    settings.KEEP_SIMPLE_CLASSES_IN_ONE_LINE = true
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