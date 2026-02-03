class Foo {

    boolean foo(Object o, final PairFunction<String, ElementType, Boolean> fun){
        boolean result = true;
        result |= fun.fun(path);
        if (o instanceof String) {
            o.subst<caret>
        }
    }

    void foo(String s) {}
}

interface PairFunction<T, V, U> {
  U fun(T t, V v);

}
