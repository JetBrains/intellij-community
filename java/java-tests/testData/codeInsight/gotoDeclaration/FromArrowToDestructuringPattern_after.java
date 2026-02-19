record R(int x, int y){}
class Test{
  void foo(Object o) {
    switch(o){
      case R(int <caret>w, int c)  -> System.out.println(w);
      default -> System.out.println()
    }
  }
}
