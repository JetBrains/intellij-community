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
      foo<error descr="'foo(Test.Parametrized<I>)' in 'Test.Bug1' cannot be applied to '(Test.Parametrized<capture<? super T>>)'">(param)</error>;
    }


  }

  class Bug2<T extends Integer>{
    <I extends Number> Parametrized<I> foo(Parametrized<I> param) {
      return null;
    }

    void bug1(Parametrized<? super T> param) {
      foo<error descr="'foo(Test.Parametrized<I>)' in 'Test.Bug2' cannot be applied to '(Test.Parametrized<capture<? super T>>)'">(param)</error>;
    }


  }
}