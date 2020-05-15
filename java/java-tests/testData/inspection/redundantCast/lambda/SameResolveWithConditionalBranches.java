class Cond<T, K> {
  static <A, B> Cond<A, B> create(A a, B b) {
    return null;
  }

  void m(boolean a, Object o){
    Cond<String, String> c = Cond.create(a ? (String)o : null, "");
    Cond<String, String> c1 = Cond.create(a ? (String)o : "null", a ? (String)o : "null");
  }
}