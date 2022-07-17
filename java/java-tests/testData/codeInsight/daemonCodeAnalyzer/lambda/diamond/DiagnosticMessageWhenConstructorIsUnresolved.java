class Test {
  {
    D<String> ds = new D<><error descr="Cannot resolve constructor 'D(int)'">(9)</error>;
  }
}

class D<T> {
  D() {}
  D(D<T> d){}
  D(M<T> m){}
}

class M<K> extends D<K> {}
