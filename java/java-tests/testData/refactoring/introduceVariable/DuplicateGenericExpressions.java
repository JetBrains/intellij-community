class Foo2<T> {
    Foo2<? extends Runnable> getList() {
        return null;
    }

    {
        Foo2<T> f = new Foo2<T>();
        Object a = <selection>f.getList()</selection>;
        Object b = f.getList();
    }
}
