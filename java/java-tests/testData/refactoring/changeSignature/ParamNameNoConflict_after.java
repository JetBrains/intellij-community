class D {
    void foo(Object o, boolean b){}
}

class DImpl extends D {
    void foo(Object o1, boolean b) {
        super.foo(o1, b);
        int o = 0;
        System.out.println(o);
    }
}