class C1<<error descr="Cyclic inheritance involving 'T'"></error>T extends T> {
  private T value;

  public boolean equals(Object obj) {
    return this == obj || obj instanceof C1 && ((C1<?>)obj).value == value;
  }
}
class C2<<error descr="Cyclic inheritance involving 'A'"></error>A extends B, B extends A> {
  private A a;
  private B b;

  public boolean equals(Object obj) {
    return this == obj || obj instanceof C2 && ((C2<?, ?>)obj).a == a && ((C2<?, ?>)obj).b == b;
  }
}
