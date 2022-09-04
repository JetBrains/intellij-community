record R(int x, int y){}
class Test{
  void foo(Object o) {
    switch(o){
      case R(int w, int c) s when s<caret>.y() > 0 -> System.out.println();
      default -> System.out.println()
    }
  }
}
