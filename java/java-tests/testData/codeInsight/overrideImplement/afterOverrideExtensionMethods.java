interface A<T> {
    default void m(T t) { }
}

class MyClass<T> implements A<T> {
    @Override
    public void m(T t) {
        <selection>//To change body of implemented methods use File | Settings | File Templates.</selection>
    }

    public MyClass() {
        super();    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public int hashCode() {
        return super.hashCode();    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        return super.clone();    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public String toString() {
        return super.toString();    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();    //To change body of overridden methods use File | Settings | File Templates.
    }
}
