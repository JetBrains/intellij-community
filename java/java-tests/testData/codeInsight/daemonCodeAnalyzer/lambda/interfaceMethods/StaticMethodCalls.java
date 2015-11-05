
class Bug {

  interface Function<T, R> {
    public R apply(T t);

    static <K> Function<K, K> identity() {
      return k -> k;
    }
  }

  interface IFunction extends Function<Integer, Integer> {
    static void a() {
      Function<Integer, Integer> identity = <error descr="Static method may be invoked on containing interface class only">identity();</error>
    }
  }

  public void foo() {
    Function<Integer, Integer> f = Function.identity();

    Function<Integer, Integer> g = <error descr="Static method may be invoked on containing interface class only">f.identity();</error>

    Function<Integer, Integer> h = IFunction.<error descr="Static method may be invoked on containing interface class only">identity</error>();
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
      <error descr="Static method may be invoked on containing interface class only">T.staticMethod();</error>
    }
    
    public <T extends MyInterface & X>  void doStuff2() {
      <error descr="Static method may be invoked on containing interface class only">T.staticMethod();</error>
    }
    
    public <T extends MyImplementation & MyInterface>  void doStuff3() {
      <error descr="Static method may be invoked on containing interface class only">T.staticMethod();</error>
    }
  }
}