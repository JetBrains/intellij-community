record R(int x, int y){}
class Test{
  void foo(Object o) {
    if (o instanceof R(int <caret>w, int c) s){
        System.out.println(w);
    }
  }
}
