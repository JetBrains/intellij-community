class B<T> {

  Object[] foo(Object obj) {
    class C {}
    return <error descr="Generic array creation">new C[0]</error>;
  }


  boolean foo1(Object obj) {
    class C {}
    return obj instanceof <error descr="Illegal generic type for instanceof">C</error>;
  }

  static <A> B<A> localClassInStaticMethod() {
    class C extends B<A> {
      @Override
      public boolean equals(Object obj) {
        return obj instanceof C;
      }
    }

    return new C();
  }
  
  static interface I<T> {}
  void anonymousClassWithLocal() {
    I<Object> i = new I<Object>() {
      class InsideAnno {}
      {
        InsideAnno[] array = <error descr="Generic array creation">new InsideAnno[1]</error>;
      }
    };
  }
  
  static void staticAnonymousClassWithLocal() {
    I<Object> i = new I<Object>() {
      class InsideAnno {}
      {
        InsideAnno[] array = new InsideAnno[1];
      }
    };
  }

  static void staticAnonymousClassWithGenericLocal() {
    I<Object> i = new I<Object>() {

      class InsideAnno<J> {
        class O {}

        {
          O[] array = <error descr="Generic array creation">new O[1]</error>;
        }
      }
    };
  }
}