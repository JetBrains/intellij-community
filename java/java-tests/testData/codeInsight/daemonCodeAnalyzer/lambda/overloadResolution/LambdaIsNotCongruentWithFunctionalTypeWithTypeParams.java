interface I {
  void g();

}
interface J {
  <<warning descr="Type parameter 'T' is never used">T</warning>> void f();
}
class Test {
  void m(I i) {System.out.println(i);}
  void m(J j) {System.out.println(j);}

  void m2(J j){System.out.println(j);}

  {
    m (() -> {});
    m2(<error descr="Target method is generic">() -> {}</error>);
  }
}