class A {
}


class B extends A {
}

class C {
   void foo(Object o) {
       if (o instanceof A || <warning descr="Condition 'o instanceof B' is always 'false' when reached">o instanceof B</warning>) {
           System.out.println("Something");
       }
   }
   void bar(A a) {
     if (<warning descr="Condition 'a instanceof A' is redundant and can be replaced with a null check">a instanceof A</warning>) {}
     else if (<warning descr="Condition 'a instanceof B' is always 'false'">a instanceof B</warning>) {}
   }
}