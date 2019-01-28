package pck;

class A {
    public void bar(I a, Class<Long> any) {
        System.out.println(a.with(any));
    }

    interface I {
        <error descr="'with(Class<T>)' clashes with 'with(Class<Long>)'; both methods have same erasure"><T> T with(Class<T> aClass)</error>;
        long with(Class<Long> aClass);
    }
}

