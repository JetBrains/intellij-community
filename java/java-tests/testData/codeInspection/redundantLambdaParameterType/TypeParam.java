// "Remove redundant types" "false"
class Test3 {
  interface I<Y> {
    void m(Y y);
  }

  static <T> void bar(I<T> i, List<T> l){

  }
  static void bar2(){
    bar((<caret>T x) -> {}, null);
  }
}
