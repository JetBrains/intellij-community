class A {
  {
    Func2<String,String> f = ge<caret>x
  }

  <T,V> Func1<T,V> get1() {}
  <T,V> Func2<T,V> get2() {}

}


interface Func1<T, V> {}
interface Func2<T, V> {}