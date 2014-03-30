interface X {
    A[] foo();
}

class A {}

class B extends A {}

class C {
    final X x = new X() {
        @Override
        public B[] foo() {
            return new B[0];
        }
    };

    B[] bar() {
        return x.f<ref>oo();
    }
}