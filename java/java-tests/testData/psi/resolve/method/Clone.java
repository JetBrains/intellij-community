class Z{
  public interface A { Object clone(); }

  interface B extends A { }




  class C implements B {
    Object clone() {
      C c = new C();
      ((A) c).clone();
      ((B) c).<ref>clone();
      return c.clone();
    }
  }
}
