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
}