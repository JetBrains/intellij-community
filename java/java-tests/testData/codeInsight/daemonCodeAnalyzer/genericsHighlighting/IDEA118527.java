
interface Listener<A0, B0> {}
class Adapter<A, B> implements Listener<A, B> {}
class Super<O, B> {
  public <C extends Adapter<O, B>> C apply(C configurer) throws Exception {
    return null;
  }

  public <C extends Listener<O, B>> C apply(C configurer) throws Exception {
    return null;
  }
}

class AdapterImpl extends Adapter<String, String>{}

class Child<O, B> extends Super<O, B> {}
class D {

  void foo(Child<String, String> ch, AdapterImpl a) throws Exception {
    ch.apply(a);
  }
}

class Test {
  interface Listener<A0, B0> {}
  class Adapter<A, B> implements Listener<A, B> {}
  class Super<O, B> {
    public <C extends Adapter<O, B> & Runnable> C apply(C configurer) throws Exception {
      return null;
    }

    public <C extends Listener<O, B> & Runnable> C apply(C configurer) throws Exception {
      return null;
    }
  }

  abstract class AdapterImpl extends Adapter<String, String> implements Runnable{}

  class Child<O, B> extends Super<O, B> {}
  class D {

    void foo(Child<String, String> ch, AdapterImpl a) throws Exception {
      ch.apply(  a);
    }
  }


}

class Test1 {
  class ResolveResult {
  }

  class A {
  }

  class B extends A {
  }

  abstract class AbstractResolver<K, V> {
  }

  abstract class Resolver<K> extends AbstractResolver<K, ResolveResult[]> {
  }

  public <T extends B> ResolveResult[] resolveWithCaching(T ref, Resolver<T> resolver) {
    return null;
  }

  public <TRef extends A, TResult> TResult resolveWithCaching(TRef ref, AbstractResolver<TRef, TResult> resolver) {
    return null;
  }

  {
    Resolver<B> resolver = null;
    B b = null;
    resolveWithCaching(b, resolver);
  }
}