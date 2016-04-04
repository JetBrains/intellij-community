
class A<T extends Iterable<?>>{
  T get() {return null;}
  T x;
  void foo(A<? extends Iterable<String> > a){
    String s = bar(a.get());
    String s1 = bar(a.x);
  }
  <Tb> Tb bar(Iterable<Tb> a){
    return null;
  }
}
