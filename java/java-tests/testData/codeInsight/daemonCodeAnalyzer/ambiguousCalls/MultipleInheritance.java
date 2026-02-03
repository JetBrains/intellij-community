class A<T>{}

    interface I<Q extends I<Q>> {
        Q from(A<?>... paths);
    }

    interface II extends I<II> {}
    class C<Q extends C<Q>> {
        public Q from(A<?>... args) {return null;}
    }
    class AC<Q extends AC<Q>>  extends C<Q> {}
    class CC extends AC<CC> implements II {
      void bar() {
        from(null);
      }
      
      static void barStatic() {
        <error descr="Non-static method 'from(A<?>...)' cannot be referenced from a static context">from</error>(null);
      }
    }

class Test {
 
    void foo(CC a){
        a.from(null);
    }

}