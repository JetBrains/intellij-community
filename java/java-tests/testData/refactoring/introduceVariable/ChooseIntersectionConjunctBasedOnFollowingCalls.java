
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
    <selection>x.c()</selection>.a();
  }
}