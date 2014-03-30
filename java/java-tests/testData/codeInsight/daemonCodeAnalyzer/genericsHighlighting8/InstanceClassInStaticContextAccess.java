class MyTest<K> {

    class A<T> {
    }

    //not an error in java 8?!
    static class C<T extends <error descr="'MyTest.this' cannot be referenced from a static context">A<String></error>> {
    }

    static <T extends <error descr="'MyTest.this' cannot be referenced from a static context">A<String></error>> void bar() {
    }

    static class B {
        {
            B.<<error descr="'MyTest.this' cannot be referenced from a static context">A</error>>bar();
            <error descr="'MyTest.this' cannot be referenced from a static context">A</error> a;
        }

        static <T extends <error descr="'MyTest.this' cannot be referenced from a static context">A<String></error>> void bar() {
        }

        void v(C<<error descr="'MyTest.this' cannot be referenced from a static context">A<String></error>> c) {
        }
    }
}

class MyTest1 {

    class A<T> {
    }

    static class C<T extends A<String>> {
    }

    static <T extends A<String>> void bar() {
    }

    static class B {
        {
            B.<A>bar();
            A a = <error descr="'MyTest1.this' cannot be referenced from a static context">new A()</error>;
        }

        static <T extends A<String>> void bar() {
        }

        void v(C<A<String>> c) {
        }
    }
}

class MyTest2<T> {
    static class A {
      private MyTest2 myTest;
  
      public Object foo() {
        return myTest.new Bar();
      }
    }

    class Bar {}
}
