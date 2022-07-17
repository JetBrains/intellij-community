record R(int x, int y){}
class Test{
  void foo(Object o) {
    if (o instanceof R(int w, int c) <caret>s){
        System.out.println(s);
    }
  }
}
