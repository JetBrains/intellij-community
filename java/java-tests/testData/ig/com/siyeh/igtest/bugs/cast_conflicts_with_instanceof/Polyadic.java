interface I {}
class A implements I {}
class B implements I {}

class U {
  void foo(Object o) {
    boolean b = false;
    if (o instanceof I) {
      b = o instanceof A && bar((A)o) && !bazz((A)o);
    }
  }
  
  boolean bar(A a){ return true;}
  boolean bazz(A a){ return false;}
}
