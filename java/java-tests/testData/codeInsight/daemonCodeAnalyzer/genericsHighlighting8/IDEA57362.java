class C<T,S> {
    class D extends C<D,D> {}
    <T> T foo(){
        <error descr="Incompatible types. Found: 'C.D.D.D', required: 'C.D'">D x = this.<D.D.D>foo();</error>
        return null;
    }
}