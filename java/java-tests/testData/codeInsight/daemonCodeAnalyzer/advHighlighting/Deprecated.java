// deprecated

class a  {
  /**
  * @deprecated
  */
  void f() {}

  /**
  * @deprecated
  */
  int dep;
  int notdep;

  /**
  * @deprecated
  */
  a(int i,int j,int k) {
     new <warning descr="'a(int, int, int)' is deprecated">a</warning>(k+i+j,<warning descr="'dep' is deprecated">dep</warning>,notdep);
  }
}

class b extends a {
  void <warning descr="Overrides deprecated method in 'a'">f</warning>() {
    super.<warning descr="'f()' is deprecated">f</warning>();
  }

  b() {
    <warning descr="'b(int)' is deprecated">this</warning>(1);
  }

  /**
  * @deprecated
  */
  b(int i) {
    <warning descr="'a(int, int, int)' is deprecated">super</warning>(0,0,i);
    System.out.print(i);
  }
}

class c extends b {
  // b.f is not deprecated 
  void f() {}
}

interface i1 {
  /**
  * @deprecated
  */
  void f();
}
abstract class ac1 {
  /**
  * @deprecated
  */
  public abstract void f();
}

class ci1 extends ac1 implements i1 {
 // no chance not to implement it
 public void f() {}
}