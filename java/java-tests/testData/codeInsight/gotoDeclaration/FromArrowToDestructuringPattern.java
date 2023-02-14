record R(int x, int y){}
class Test{
  void foo(Object o) {
    switch(o){
      case R(int w, int c) s  -> System.out.println(w<caret>);
      default -> System.out.println()
    }
  }
}
