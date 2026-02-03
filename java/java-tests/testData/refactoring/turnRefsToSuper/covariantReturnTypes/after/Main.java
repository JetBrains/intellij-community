interface Int {
    Int f();
}
class Impl implements Int {
    void foo(){}
    Impl f() {return this;}
}

class Usage {
    void fk(Impl l, Impl ll){
      l.f().foo();

      final Impl f = ll.f();
      f.foo();
    }
}
