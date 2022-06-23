record R(int x, int y){}
class Test{
  void foo(Object o) {
    switch(o){
      case R(int w, int c) s  -> System.out.println(s<caret>);
      default -> System.out.println()
    }
  }
}
