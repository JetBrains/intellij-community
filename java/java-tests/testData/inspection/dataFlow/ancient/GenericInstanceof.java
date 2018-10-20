class Generic<T> {
  Generic() {}
}

class Test {
  void foo () {
    Generic g = new Generic ();
    if (<warning descr="Condition 'g instanceof Generic<String>' is always 'true'">g instanceof <error descr="Illegal generic type for instanceof">Generic<String></error></warning>) {
      return;
    }
  }
}