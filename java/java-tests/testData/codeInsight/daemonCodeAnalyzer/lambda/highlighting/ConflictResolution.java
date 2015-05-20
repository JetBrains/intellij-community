class Demo {

    public void f1() {
        f2(2, <error descr="Target type of a lambda conversion must be an interface">input -> input</error>);
    }

    public void f2() {
    }

    public void f2(Object... params) {
    }
}
