import java.util.List;

interface TT<T extends List<T>> {
  boolean p(T t);
}

class TU {
  void foo(TT<? extends List<?>> k){}

  {
    foo(<error descr="Cannot infer functional interface type">x -> false</error>);
  }
}

interface TT1<T extends List<?>> {
  boolean p(T t);
}

class TU1 {
  void foo(TT1<? extends List<?>> k){}

  {
    foo(x -> false);
  }
}


interface TT2<T> {
  boolean p(T t);
}

class TU2 {
  void foo(TT2<? extends List<?>> k){}

  {
    foo(x -> false);
  }
}
