class Test {
  public <T> void foo(T valIn){
    double val = (double ) valIn;
  }
  
  public <T extends Double> void foo1(T valIn){
    double val = (double ) valIn;
  }

  public <T extends String> void foo2(T valIn){
    double val = <error descr="Inconvertible types; cannot cast 'T' to 'double'">(double ) valIn</error>;
  }

  public <T extends S, S extends Double> void foo2(T valIn){
    double val = (double ) valIn;
  }
}

class Foo<T> {

  private T _value;

  T getValue() {
    return _value;
  }

  static Foo<?> getFoo() {
    return new Foo<>();
  }

  public static void main(String[] args) {
    Foo<?> foo = getFoo();
    double value = (double) foo.getValue();
  }
}