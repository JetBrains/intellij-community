import java.io.Serializable;

interface Parametrized<T extends Number> {}

class Bug1<T extends Serializable>{
  <I extends Number> Parametrized<I> foo(Parametrized<I> param) {
    return null;
  }

  void bug1(Parametrized<? super T> param) {
    foo(param);
  }


}

class Bug2<T extends Integer>{
  <I extends Number> Parametrized<I> foo(Parametrized<I> param) {
    return null;
  }

  void bug1(Parametrized<? super T> param) {
    foo(param);
  }


}

class Test {
  interface Parametrized<T extends Serializable> {}

  class Bug1<T extends Serializable>{
    <I extends Number> Parametrized<I> foo(Parametrized<I> param) {
      return null;
    }

    void bug1(Parametrized<? super T> param) {
      <error descr="Inferred type 'java.io.Serializable' for type parameter 'I' is not within its bound; should extend 'java.lang.Number'">foo(param)</error>;
    }


  }

  class Bug2<T extends Integer>{
    <I extends Number> Parametrized<I> foo(Parametrized<I> param) {
      return null;
    }

    void bug1(Parametrized<? super T> param) {
      <error descr="Inferred type 'java.io.Serializable' for type parameter 'I' is not within its bound; should extend 'java.lang.Number'">foo(param)</error>;
    }


  }
}