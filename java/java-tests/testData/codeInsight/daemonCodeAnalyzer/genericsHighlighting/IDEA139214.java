class JavaClass {
  interface I1<T> {
    void id(T x);
  }

  static class C1<T> {
    void id(T x) {
    }
  }

  <error descr="'id(T)' in 'JavaClass.I1' clashes with 'id(T)' in 'JavaClass.C1'; both methods have same erasure, yet neither overrides the other">static class C2 extends C1<String> implements I1<Integer></error> {
    public void id(Integer x) {
    }
  }
}