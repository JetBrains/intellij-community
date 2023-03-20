record R(int x, int y){}
class Test{
  void foo(Object o) {
    switch(o){
      case R(int w, int c) <caret>s:
        System.out.println(s);
        break;
      default -> System.out.println()
    }
  }
}
