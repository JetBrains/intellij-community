class A {
    protected void foo() {}
}

class C {
    final A a = new A() {
        @Override
        public void foo() {}
    };

    {
        a.f<ref>oo();
    }
}