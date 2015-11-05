class Test {
  {
    D<String> ds = new D<<error descr="Cannot infer arguments (unable to resolve constructor)"></error>>(9);
  }
}

class D<T> {
  D() {}
  D(D<T> d){}
  D(M<T> m){}
}

class M<K> extends D<K> {}
