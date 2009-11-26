class Foo2<T> {
    Foo2<? extends Runnable> getList() {
        return null;
    }

    {
        Foo2<T> f = new Foo2<T>();
        Foo2<? extends Runnable> temp = f.getList();
        Object a = temp;
        Object b = temp;
    }
}
