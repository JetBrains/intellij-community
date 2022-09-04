record R(int x, int y){}
class Test{
  void foo(Object o) {
    switch(o){
      case R(int w, int <caret>c) s when c > 0 -> System.out.println();
      default -> System.out.println()
    }
  }
}
