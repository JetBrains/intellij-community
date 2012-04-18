package pck;
class Test {
   void test() {
       B.method(new ArgumentB());
   }
}

class A {
   static void method(ArgumentA a) { }
}

class B extends A {
   static void method(ArgumentB b) { }
}

class ArgumentA<T> {}
class ArgumentB extends ArgumentA<Object> {}