
class Bug {

  interface Function<T, R> {
    public R apply(T t);

    static <K> Function<K, K> identity() {
      return k -> k;
    }
  }

  interface IFunction extends Function<Integer, Integer> {
    static void a() {
      Function<Integer, Integer> identity = <error descr="Static method may only be called on its containing interface">identity</error>();
    }
  }

  public void foo() {
    Function<Integer, Integer> f = Function.identity();

    Function<Integer, Integer> g = f.<error descr="Static method may only be called on its containing interface">identity</error>();

    Function<Integer, Integer> h = IFunction.<error descr="Static method may only be called on its containing interface">identity</error>();
  }
}

class StaticMethodInterfaceExample {

  interface X {}
  interface MyInterface {
    static void staticMethod() {}
  }

  static class MyImplementation implements MyInterface { }

  public static class Usage {

    public <T extends MyInterface>  void doStuff() {
      T.staticMethod();
    }
    
    public <T extends MyImplementation>  void doStuff1() {
      T.<error descr="Static method may only be called on its containing interface">staticMethod</error>();
    }
    
    public <T extends MyInterface & X>  void doStuff2() {
      T.<error descr="Static method may only be called on its containing interface">staticMethod</error>();
    }
    
    public <T extends MyImplementation & MyInterface>  void doStuff3() {
      T.<error descr="Static method may only be called on its containing interface">staticMethod</error>();
    }
  }
}
class StaticMethodInterfaceExample2 {
  interface MyInterface {
    static void m(int a) { }
  }

  class MyClass {
    void m() { }

    class MyInnerClass implements MyInterface {
      {
        m();
      }
    }
  }
}

interface StaticMethodInterfaceExample3 {
  static void m() { }

  class MyClass implements StaticMethodInterfaceExample3 {
      {
          m();
      }
  }
}