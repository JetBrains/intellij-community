interface Foo<T> {
  void run(T t, int myInt);
  void run2(T t, int myInt);
}

class A implements Foo<String> {
    public A() {
        <selection><caret>super();</selection>
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    @Override
    public String toString() {
        return super.toString();
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
    }

    @Override
    public void run(String s, int myInt) {

    }

    @Override
    public void run2(String s, int myInt) {

    }

    void foo() {}
}
