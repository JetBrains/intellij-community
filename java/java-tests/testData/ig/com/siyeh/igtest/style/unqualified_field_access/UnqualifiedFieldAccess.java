package com.siyeh.igtest.style.unqualified_field_access;

public class UnqualifiedFieldAccess {

        private String field;

        public void x () {
                <warning descr="Instance field access 'field' is not qualified with 'this'">field</warning> = "foofoo";
                final String s = String.valueOf(<warning descr="Instance field access 'field' is not qualified with 'this'">field</warning>.hashCode());
                System.out.println(s);
        }

  void foo() {
    new Object() {
      int i;
      void foo() {
        new Object() {
          void foo() {
            <warning descr="Instance field access 'i' is not qualified with 'this'">i</warning>  = 0;
          }
        };
      }
    };
    class A {
      int i;
      void a() {
        new Object() {
          void b() {
            System.out.println(<warning descr="Instance field access 'i' is not qualified with 'this'">i</warning>);
          }
        };
      }
    }
  }

  void simpleAnonymous() {
    new Object() {
      String s;

      void foo() {
        System.out.println(<warning descr="Instance field access 's' is not qualified with 'this'">s</warning>);
      }
    };
  }
}