class C<T,S> {
    class D extends C<D,D> {}
    <T> T foo(){
        D x = this.<D.D.D><error descr="Incompatible types. Found: 'C.D.D.D', required: 'C.D'">foo</error>();
        return null;
    }
}