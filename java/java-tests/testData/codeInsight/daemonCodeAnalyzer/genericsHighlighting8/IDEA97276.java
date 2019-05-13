interface Interf<X extends Interf> {}
class SomeClass {
    static <I extends Interf<? super I>> Class<I> someMethod(I i) { return null; }
}

interface OtherInterf<I1 extends Interf, I2 extends Interf> {}
interface ImmutableSet<S> {}

class SomeOtherClass {
  static ImmutableSet<Class<? extends OtherInterf<?, ?>>> someOtherMethod() {
    return <error descr="Inconvertible types; cannot cast 'ImmutableSet<java.lang.Class<? extends OtherInterf>>' to 'ImmutableSet<java.lang.Class<? extends OtherInterf<?,?>>>'">(ImmutableSet<Class<? extends OtherInterf<?, ?>>>)aux(OtherInterf.class)</error>;
  }

  static <T> ImmutableSet<Class<? extends T>> aux(Class<T> t) {
    return null;
  }
}
