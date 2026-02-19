
interface P1<T1> { boolean test(T1 t);}
interface P2<T2> { boolean test(T2 t);}

class C {
  public C(P1<String> c) { }
  public C(P2<String> p) { }
}

class C1 extends C {
  public C1() {
    super(new P2<>() {
      public boolean test(String s) {
        return false;
      }
    });
  }
}