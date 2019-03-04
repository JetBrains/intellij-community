enum E {
    A() {
        public void foo(){}
    };
    public void foo() {}
}

class Bar {
    {
        E.A.f<caret>oo();
    }
}