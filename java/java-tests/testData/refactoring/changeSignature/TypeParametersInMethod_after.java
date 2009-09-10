class C<T> {
    protected <U> U method(T t, U u, C<U> cu){
    }
}

class C1 extends C<String> {
    protected <V> V method(String t, V u, C<V> cu){
    }
}