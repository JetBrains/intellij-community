record R(int x, int y){}
class Test{
  void foo(Object o) {
    switch(o){
      case R(int w, int <caret>c) s:
        System.out.println(c);
        break;
      default -> System.out.println()
    }
  }
}
