class Demo {

    public void f1() {
        f2<error descr="'f2()' in 'Demo' cannot be applied to '(int, <lambda expression>)'">(2, input -> input)</error>;
    }

    public void f2() {
    }

    public void f2(Object... params) {
    }
}
