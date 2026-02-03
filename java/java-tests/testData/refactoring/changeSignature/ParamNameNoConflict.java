class D {
    void f<caret>oo(Object o){}
}

class DImpl extends D {
    void foo(Object o1) {
        super.foo(o1);
        int o = 0;
        System.out.println(o);
    }
}