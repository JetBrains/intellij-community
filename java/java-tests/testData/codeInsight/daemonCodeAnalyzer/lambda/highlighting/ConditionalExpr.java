class Test {
  public interface I {
    int m();
  }

  public interface I1 {
    int m(int y);
  }

  {
    boolean flag = true;
    I i =  flag ? (() -> 123)   : (() -> 222);
    I i1 =  flag ? (() -> {<error descr="Missing return statement">}</error>)   : (() -> 222);
    Object i2 =  flag ? (<error descr="Target type of a lambda conversion must be an interface">() -> 42</error>)   : (<error descr="Target type of a lambda conversion must be an interface">() -> 222</error>);
    I i3 =  flag ? (<error descr="Incompatible parameter types in lambda expression: wrong number of parameters: expected 0 but found 1">(x)</error> -> 42)   : (() -> 222);
    I i4 =  flag ? (() -> 42) : new I() {
      @Override
      public int m() {
        return 0;
      }
    };
  }
}

class Test1 {
  interface I<T, V> {
    V m(T t);
  }

  static <V> void bar(I<String, V> ii, I<V, String> ik){}

  {
    bar(s -> s.equals("") ? 0 : 1, i -> "");
  }
}