record R(int x, int y){}
class Test{
  void foo(Object o) {
    switch(o){
      case R(int w, int c) s:
        System.out.println(c<caret>);
        break;
      default -> System.out.println()
    }
  }
}
