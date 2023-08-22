interface I<T> {
  void m(T t);
}
class B {
  <N> void n(){
    I<N> i = i1 -> {
      //c1
      System.out.prin<caret>tln(i1);//c2
      System.out.println(i1);
    }
  }
}