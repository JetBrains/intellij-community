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
     new a(k+i+j,dep,notdep);
  }
}

class b extends a {
  void <warning descr="Overrides deprecated method in 'a'">f</warning>() {
    super.<warning descr="'f()' is deprecated">f</warning>();
  }

  b() {
    this(1);
  }

  /**
  * @deprecated
  */
  b(int i) {
    super(0,0,i);
    System.out.print(i);
  }
}

class c extends b {
  c(int i) {
    <warning descr="'b(int)' is deprecated">super</warning>(i);
  }

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

class Aaa {
  public Aaa(String s) {
    System.out.println(s);
  }

  /**
   * @deprecated
   */
  public Aaa() {}

  public void foo() {
    new Aaa("asdasdad") {};
  }
}

class Anonym {
    /**
     * @deprecated
     */
    public Anonym(String sss) {
        System.out.println(sss);
    }
    public void foo() {
        new Anonym("asdasd") {};
    }
}

class UseAnonym {
    public void foo() {
        new <warning descr="'Anonym(java.lang.String)' is deprecated">Anonym</warning>("asdasd") {};
    }
}
