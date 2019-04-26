class F {
  static E myE;
  static E getE() { return myE; }

  {
    E e = <caret>
  }

  <T> T getGeneric(Class<T> c) {}
}

class E {}