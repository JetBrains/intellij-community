interface Interf<X extends Interf> {}
class SomeClass {
    static <I extends Interf<? super I>> Class<I> someMethod(I i) { return null; }
}
