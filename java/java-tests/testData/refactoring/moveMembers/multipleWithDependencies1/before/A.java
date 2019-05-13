public class A {
      static void foo() {
      }
  
      static class Bar {
          void foo() {
              A.foo();
          }
      }
}
