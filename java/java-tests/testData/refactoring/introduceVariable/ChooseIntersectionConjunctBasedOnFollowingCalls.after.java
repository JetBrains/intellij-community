
interface IA {
  void a();
}

interface IB {
  void b();
}

interface IC<T extends IA>{
  T c();
}

class K {
  void foo(IC<? extends IB> x){
      IA m = x.c();
      m.a();
  }
}